package com.duyikang.rental.module.appeal;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.duyikang.rental.common.constant.ErrorCode;
import com.duyikang.rental.common.constant.VerdictType;
import com.duyikang.rental.common.exception.BizException;
import com.duyikang.rental.module.appeal.dto.AppealVerdictDTO;
import com.duyikang.rental.module.appeal.entity.Appeal;
import com.duyikang.rental.module.appeal.mapper.AppealMapper;
import com.duyikang.rental.module.appeal.service.AppealService;
import com.duyikang.rental.module.credit.entity.CreditPolicy;
import com.duyikang.rental.module.credit.mapper.CreditPolicyMapper;
import com.duyikang.rental.module.credit.entity.CreditLog;
import com.duyikang.rental.module.credit.mapper.CreditLogMapper;
import com.duyikang.rental.module.item.entity.Item;
import com.duyikang.rental.module.item.mapper.ItemMapper;
import com.duyikang.rental.module.order.entity.OrderOccupancy;
import com.duyikang.rental.module.order.entity.RentalOrder;
import com.duyikang.rental.module.order.mapper.OrderOccupancyMapper;
import com.duyikang.rental.module.order.mapper.RentalOrderMapper;
import com.duyikang.rental.module.settlement.dto.OrderReturnDTO;
import com.duyikang.rental.module.settlement.dto.SettlementDisputeDTO;
import com.duyikang.rental.module.settlement.entity.FeeFlow;
import com.duyikang.rental.module.settlement.entity.Settlement;
import com.duyikang.rental.module.settlement.mapper.FeeFlowMapper;
import com.duyikang.rental.module.settlement.mapper.SettlementMapper;
import com.duyikang.rental.module.settlement.service.SettlementService;
import com.duyikang.rental.module.settlement.vo.SettlementDetailVO;
import com.duyikang.rental.security.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 申诉裁决的四种裁定（K3）。
 *
 * <p>测的核心不是"能不能改金额"，而是<b>方向</b>：双方都能申诉，承租方胜诉要把损耗费清零，
 * 出租方胜诉要把损耗费恢复——用布尔 {@code accepted} 表达时后者会被算成前者，
 * 出租方赢了官司反而自己承担损失。</p>
 */
@SpringBootTest
@Transactional
class AppealIntegrationTest {

    private static final Long OWNER = 2L;

    private static final Long RENTER = 3L;

    private static final Long ADMIN = 1L;

    private static final Integer POLICY_ID_INT = 1;

    private static final BigDecimal ITEM_LOSS_BEFORE = new BigDecimal("0.5000");

    @Autowired
    private AppealService appealService;

    @Autowired
    private CreditPolicyMapper creditPolicyMapper;

    @Autowired
    private SettlementService settlementService;

    @Autowired
    private AppealMapper appealMapper;

    @Autowired
    private SettlementMapper settlementMapper;

    @Autowired
    private FeeFlowMapper feeFlowMapper;

    @Autowired
    private RentalOrderMapper rentalOrderMapper;

    @Autowired
    private OrderOccupancyMapper orderOccupancyMapper;

    @Autowired
    private ItemMapper itemMapper;

    @Autowired
    private CreditLogMapper creditLogMapper;

    /**
     * 把当日加分上限抬高：CreditService 的正向变动受 reward_daily_cap（默认 +4）约束，
     * 而库里真实使用过的账号"今天已经加过 4 分"，会把测试里的 +2 / +10 直接吞掉（表现为一条流水都没有）。
     * 事务回滚后策略值恢复原样，不改数据库真实配置。
     */
    @BeforeEach
    void raiseCreditDailyCap() {
        CreditPolicy policy = new CreditPolicy();
        policy.setId(POLICY_ID_INT);
        policy.setRewardDailyCap(200);
        creditPolicyMapper.updateById(policy);
    }
    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("裁定：支持承租方 → 损耗费归零、应退重算、追加 OUT 冲正流水、物品损耗回滚")
    void verdict_renterWin_zeroesLossFeeAndRollsBackItemLoss() {
        long overdueDays = 5;
        Long itemId = seedItem(ITEM_LOSS_BEFORE);
        Long orderId = seedOrder(itemId, LocalDate.now().minusDays(10), LocalDate.now().minusDays(5));

        loginAs(OWNER, "张伟", "USER");
        SettlementDetailVO vo = settlementService.returnItem(orderId, new OrderReturnDTO("DAMAGED", null));

        Settlement before = settlementMapper.selectById(vo.id());
        BigDecimal originalLossFee = before.getLossFee();
        BigDecimal overdueFee = before.getOverdueFee();
        assertThat(originalLossFee).isGreaterThan(BigDecimal.ZERO);
        assertThat(itemMapper.selectById(itemId).getLossValue())
                .isEqualByComparingTo(ITEM_LOSS_BEFORE.add(before.getLossRatio()));

        loginAs(RENTER, "李娜", "USER");
        settlementService.dispute(vo.id(), new SettlementDisputeDTO("裂缝本来就有，请核查归还时的照片", null));
        Appeal appeal = appealOf(orderId);

        loginAs(ADMIN, "平台管理员", "ADMIN");
        appealService.verdict(appeal.getId(), new AppealVerdictDTO(VerdictType.RENTER_WIN, null, null, "照片证明旧裂痕，损耗不成立"));

        Appeal after = appealMapper.selectById(appeal.getId());
        assertThat(after.getStatus()).isEqualTo("ACCEPTED");
        assertThat(after.getVerdictType()).isEqualTo(VerdictType.RENTER_WIN);

        Settlement settlement = settlementMapper.selectById(vo.id());
        assertThat(settlement.getStatus()).isEqualTo("ADJUSTED");
        assertThat(settlement.getLossFee()).isEqualByComparingTo("0.00");
        assertThat(settlement.getRefundDeposit()).isEqualByComparingTo(
                new BigDecimal("100.00").subtract(overdueFee).setScale(2, RoundingMode.HALF_UP));

        FeeFlow adjust = adjustFlow(vo.id());
        assertThat(adjust.getDirection()).isEqualTo("OUT");
        assertThat(adjust.getAmount()).isEqualByComparingTo(originalLossFee);

        // 物品累计损耗回滚到裁定前
        assertThat(itemMapper.selectById(itemId).getLossValue()).isEqualByComparingTo(ITEM_LOSS_BEFORE);

        // 信誉：DAMAGE 扣分用 ADMIN_ADJUST 回滚（历史流水不动）
        CreditLog rollback = creditLogMapper.selectOne(Wrappers.<CreditLog>lambdaQuery()
                .eq(CreditLog::getUserId, RENTER)
                .eq(CreditLog::getOrderId, orderId)
                .eq(CreditLog::getReasonCode, "ADMIN_ADJUST"));
        assertThat(rollback).isNotNull();
        assertThat(rollback.getChange()).isPositive();
        assertThat(overdueDays).isEqualTo(settlement.getOverdueDays() == null ? overdueDays : settlement.getOverdueDays());
    }

    @Test
    @DisplayName("裁定：支持出租方 → 损耗费按 lossRatio 恢复、应退变少、追加 IN 冲正流水")
    void verdict_ownerWin_restoresLossFeeAndWritesInFlow() {
        Long itemId = seedItem(ITEM_LOSS_BEFORE);
        Long orderId = seedOrder(itemId, LocalDate.now().minusDays(10), LocalDate.now().minusDays(5));

        // 出租方归还时按完好验收 → 损耗费很小；随后承租方提异议（金额未变，模拟"已被判过一次减免"的状态）
        loginAs(OWNER, "张伟", "USER");
        SettlementDetailVO vo = settlementService.returnItem(orderId, new OrderReturnDTO("INTACT", null));

        loginAs(RENTER, "李娜", "USER");
        settlementService.dispute(vo.id(), new SettlementDisputeDTO("我认为根本没有损耗，要求全退", null));
        Appeal appeal = appealOf(orderId);

        // 模拟"上一次裁定把损耗费清零、应退拉满"的现场
        Settlement patched = new Settlement();
        patched.setId(vo.id());
        patched.setLossFee(new BigDecimal("0.00"));
        patched.setRefundDeposit(new BigDecimal("30.00"));
        settlementMapper.updateById(patched);

        Settlement before = settlementMapper.selectById(vo.id());
        BigDecimal expectedLossFee = before.getDepositAmount().multiply(before.getLossRatio())
                .setScale(2, RoundingMode.HALF_UP);

        loginAs(ADMIN, "平台管理员", "ADMIN");
        appealService.verdict(appeal.getId(), new AppealVerdictDTO(VerdictType.OWNER_WIN, null, null, "归还时有照片为证，损耗成立"));

        Settlement settlement = settlementMapper.selectById(vo.id());
        assertThat(settlement.getStatus()).isEqualTo("ADJUSTED");
        assertThat(settlement.getLossFee()).isEqualByComparingTo(expectedLossFee);
        assertThat(settlement.getRefundDeposit()).isEqualByComparingTo(
                before.getDepositAmount().subtract(before.getOverdueFee()).subtract(expectedLossFee)
                        .setScale(2, RoundingMode.HALF_UP));

        FeeFlow adjust = adjustFlow(vo.id());
        assertThat(adjust.getDirection()).isEqualTo("IN");
        assertThat(adjust.getAmount()).isEqualByComparingTo(expectedLossFee);
    }

    @Test
    @DisplayName("裁定：部分支持 → 按裁定金额落库，物品损耗按比例回滚")
    void verdict_partial_usesOverrideAndProratesRollback() {
        Long itemId = seedItem(ITEM_LOSS_BEFORE);
        Long orderId = seedOrder(itemId, LocalDate.now().minusDays(10), LocalDate.now().minusDays(5));

        loginAs(OWNER, "张伟", "USER");
        SettlementDetailVO vo = settlementService.returnItem(orderId, new OrderReturnDTO("DAMAGED", null));
        Settlement before = settlementMapper.selectById(vo.id());
        BigDecimal originalLossFee = before.getLossFee();
        BigDecimal weight = before.getLossRatio();
        BigDecimal override = originalLossFee.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);

        loginAs(RENTER, "李娜", "USER");
        settlementService.dispute(vo.id(), new SettlementDisputeDTO("磨损没这么严重，全额扣损耗不合理", null));
        Appeal appeal = appealOf(orderId);

        loginAs(ADMIN, "平台管理员", "ADMIN");
        appealService.verdict(appeal.getId(),
                new AppealVerdictDTO(VerdictType.PARTIAL, override, null, "双方各有责任，损耗费减半"));

        Settlement settlement = settlementMapper.selectById(vo.id());
        assertThat(settlement.getStatus()).isEqualTo("ADJUSTED");
        assertThat(settlement.getLossFee()).isEqualByComparingTo(override);

        FeeFlow adjust = adjustFlow(vo.id());
        assertThat(adjust.getDirection()).isEqualTo("OUT");
        assertThat(adjust.getAmount()).isEqualByComparingTo(originalLossFee.subtract(override));

        BigDecimal keepRatio = override.divide(originalLossFee, 4, RoundingMode.HALF_UP);
        BigDecimal rollback = weight.multiply(BigDecimal.ONE.subtract(keepRatio)).setScale(4, RoundingMode.HALF_UP);
        assertThat(itemMapper.selectById(itemId).getLossValue()).isEqualByComparingTo(
                ITEM_LOSS_BEFORE.add(weight).subtract(rollback).setScale(4, RoundingMode.HALF_UP));
    }

    @Test
    @DisplayName("裁定：驳回 → 金额一分不动，不产生冲正流水")
    void verdict_reject_keepsAmounts() {
        Long itemId = seedItem(ITEM_LOSS_BEFORE);
        Long orderId = seedOrder(itemId, LocalDate.now().minusDays(10), LocalDate.now().minusDays(5));

        loginAs(OWNER, "张伟", "USER");
        SettlementDetailVO vo = settlementService.returnItem(orderId, new OrderReturnDTO("DAMAGED", null));
        Settlement before = settlementMapper.selectById(vo.id());

        loginAs(RENTER, "李娜", "USER");
        settlementService.dispute(vo.id(), new SettlementDisputeDTO("我不认可这个损耗认定，要求复核", null));
        Appeal appeal = appealOf(orderId);

        loginAs(ADMIN, "平台管理员", "ADMIN");
        appealService.verdict(appeal.getId(), new AppealVerdictDTO(VerdictType.REJECTED, null, null, "验收照片充分，维持原认定"));

        Appeal after = appealMapper.selectById(appeal.getId());
        assertThat(after.getStatus()).isEqualTo("REJECTED");
        Settlement settlement = settlementMapper.selectById(vo.id());
        assertThat(settlement.getStatus()).isEqualTo("SETTLED");
        assertThat(settlement.getLossFee()).isEqualByComparingTo(before.getLossFee());
        assertThat(settlement.getRefundDeposit()).isEqualByComparingTo(before.getRefundDeposit());
        assertThat(feeFlowMapper.selectCount(Wrappers.<FeeFlow>lambdaQuery()
                .eq(FeeFlow::getSettlementId, vo.id())
                .eq(FeeFlow::getType, "ADJUST"))).isZero();
    }

    @Test
    @DisplayName("裁定：部分支持但没给金额 → 2001")
    void verdict_partialWithoutOverride_paramError() {
        Long itemId = seedItem(ITEM_LOSS_BEFORE);
        Long orderId = seedOrder(itemId, LocalDate.now().minusDays(10), LocalDate.now().minusDays(5));
        loginAs(OWNER, "张伟", "USER");
        SettlementDetailVO vo = settlementService.returnItem(orderId, new OrderReturnDTO("WORN", null));
        loginAs(RENTER, "李娜", "USER");
        settlementService.dispute(vo.id(), new SettlementDisputeDTO("磨损程度有争议，请复核实物照片", null));
        Appeal appeal = appealOf(orderId);

        loginAs(ADMIN, "平台管理员", "ADMIN");
        assertThatThrownBy(() -> appealService.verdict(appeal.getId(),
                new AppealVerdictDTO(VerdictType.PARTIAL, null, null, "部分支持但忘了填金额")))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.PARAM_ERROR.getCode()));
    }

    @Test
    @DisplayName("裁定：裁定类型非法 → 2001（挡住「随便传个值」的调用）")
    void verdict_invalidType_paramError() {
        Long itemId = seedItem(ITEM_LOSS_BEFORE);
        Long orderId = seedOrder(itemId, LocalDate.now().minusDays(10), LocalDate.now().minusDays(5));
        loginAs(OWNER, "张伟", "USER");
        SettlementDetailVO vo = settlementService.returnItem(orderId, new OrderReturnDTO("WORN", null));
        loginAs(RENTER, "李娜", "USER");
        settlementService.dispute(vo.id(), new SettlementDisputeDTO("我不认可损耗比例，要求复核", null));
        Appeal appeal = appealOf(orderId);

        loginAs(ADMIN, "平台管理员", "ADMIN");
        assertThatThrownBy(() -> appealService.verdict(appeal.getId(),
                new AppealVerdictDTO("APPROVED", null, null, "传了个不存在的裁定类型")))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.PARAM_ERROR.getCode()));
    }

    @Test
    @DisplayName("裁定：手工调整承租方信誉 → 落一条 ADMIN_ADJUST 流水")
    void verdict_withCreditAdjust_recordsAdminAdjust() {
        Long itemId = seedItem(ITEM_LOSS_BEFORE);
        Long orderId = seedOrder(itemId, LocalDate.now().minusDays(10), LocalDate.now().minusDays(5));
        loginAs(OWNER, "张伟", "USER");
        SettlementDetailVO vo = settlementService.returnItem(orderId, new OrderReturnDTO("WORN", null));
        loginAs(RENTER, "李娜", "USER");
        settlementService.dispute(vo.id(), new SettlementDisputeDTO("磨损认定偏重，请复核", null));
        Appeal appeal = appealOf(orderId);

        loginAs(ADMIN, "平台管理员", "ADMIN");
        appealService.verdict(appeal.getId(),
                new AppealVerdictDTO(VerdictType.PARTIAL, new BigDecimal("0.02"), 3, "损耗减半，另补偿信誉 3 分"));

        CreditLog adjust = creditLogMapper.selectOne(Wrappers.<CreditLog>lambdaQuery()
                .eq(CreditLog::getUserId, RENTER)
                .eq(CreditLog::getOrderId, orderId)
                .eq(CreditLog::getReasonCode, "ADMIN_ADJUST"));
        assertThat(adjust).isNotNull();
        assertThat(adjust.getChange()).isEqualTo(3);
        assertThat(adjust.getAfterScore()).isEqualTo(
                Math.min(100, Math.max(0, adjust.getBeforeScore() + 3)));
    }

    // ------------------------------------------------------------------ 工具

    private Appeal appealOf(Long orderId) {
        Appeal appeal = appealMapper.selectOne(Wrappers.<Appeal>lambdaQuery().eq(Appeal::getOrderId, orderId));
        assertThat(appeal).as("订单 %s 应当有申诉记录", orderId).isNotNull();
        return appeal;
    }

    private FeeFlow adjustFlow(Long settlementId) {
        List<FeeFlow> flows = feeFlowMapper.selectList(Wrappers.<FeeFlow>lambdaQuery()
                .eq(FeeFlow::getSettlementId, settlementId)
                .eq(FeeFlow::getType, "ADJUST"));
        assertThat(flows).as("应当有且仅有一条 ADJUST 冲正流水").hasSize(1);
        return flows.get(0);
    }

    private Long seedItem(BigDecimal lossValue) {
        Item item = new Item();
        item.setOwnerId(OWNER);
        item.setCategoryId(8L);
        item.setCommunityId(1L);
        item.setTitle("申诉裁决测试物品");
        item.setDescription("仅测试用");
        item.setCoverUrl("/uploads/202611/" + "0".repeat(32) + ".png");
        item.setDailyPrice(new BigDecimal("20.00"));
        item.setDeposit(new BigDecimal("100.00"));
        item.setOverdueRate(new BigDecimal("0.5000"));
        item.setLossValue(lossValue);
        item.setStatus("RENTED");
        itemMapper.insert(item);
        return item.getId();
    }

    private Long seedOrder(Long itemId, LocalDate start, LocalDate end) {
        RentalOrder order = new RentalOrder();
        order.setOrderNo("A" + System.nanoTime());
        order.setItemId(itemId);
        order.setOwnerId(OWNER);
        order.setRenterId(RENTER);
        order.setStartDate(start);
        order.setEndDate(end);
        order.setRentDays((int) java.time.temporal.ChronoUnit.DAYS.between(start, end));
        order.setStatus("IN_USE");
        order.setDailyPrice(new BigDecimal("20.00"));
        order.setDeposit(new BigDecimal("100.00"));
        order.setOverdueRate(new BigDecimal("0.5000"));
        order.setRentAmount(new BigDecimal("20.00").multiply(BigDecimal.valueOf(order.getRentDays())));
        rentalOrderMapper.insert(order);
        for (LocalDate day = start; day.isBefore(end); day = day.plusDays(1)) {
            OrderOccupancy occupancy = new OrderOccupancy();
            occupancy.setItemId(itemId);
            occupancy.setOrderId(order.getId());
            occupancy.setOccupyDate(day);
            orderOccupancyMapper.insert(occupancy);
        }
        return order.getId();
    }

    private void loginAs(Long userId, String nickname, String role) {
        LoginUser user = new LoginUser(userId, nickname, role);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                user, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }
}
