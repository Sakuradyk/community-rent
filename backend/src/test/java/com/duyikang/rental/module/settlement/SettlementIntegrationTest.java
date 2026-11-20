package com.duyikang.rental.module.settlement;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.duyikang.rental.common.constant.ErrorCode;
import com.duyikang.rental.common.exception.BizException;
import com.duyikang.rental.common.constant.VerdictType;
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
import com.duyikang.rental.module.loss.entity.LossLog;
import com.duyikang.rental.module.loss.mapper.LossLogMapper;
import com.duyikang.rental.module.order.entity.OrderOccupancy;
import com.duyikang.rental.module.order.entity.RentalOrder;
import com.duyikang.rental.module.order.mapper.OrderOccupancyMapper;
import com.duyikang.rental.module.order.mapper.RentalOrderMapper;
import com.duyikang.rental.module.settlement.dto.OrderReturnDTO;
import com.duyikang.rental.module.settlement.dto.SettlementDisputeDTO;
import com.duyikang.rental.module.settlement.entity.Settlement;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 切片 5 结算与信誉集成测试（K3 + K4）。
 *
 * <p>断言重点不是"接口返回 0"，而是论文里要讲的四件事：</p>
 * <ol>
 *   <li>逾期费按阶梯算对（3 天 0.5 倍 + 超出天数 2 倍）；</li>
 *   <li>损耗 w 与累计 L 同步写回物品，L 到 0.85 物品转「检修中」；</li>
 *   <li>归还后订单完成、<b>占用被释放</b>（否则那几天永久占死）；</li>
 *   <li>冷静期三个出口：确认 / 异议 / 超时自动确认，裁决通过后免损耗费。</li>
 * </ol>
 *
 * <p>物品一律现造，不依赖种子行 —— 用户会在浏览器里把种子数据改成任意状态。</p>
 */
@SpringBootTest
@Transactional
class SettlementIntegrationTest {

    private static final Long OWNER = 2L;

    private static final Long RENTER = 3L;

    private static final Long STRANGER = 4L;

    private static final String ON_TIME = "ON_TIME";

    /** credit_policy 是单行策略表，主键固定 1（字段类型是 Integer）。 */
    private static final Integer POLICY_ID_INT = 1;

    @Autowired
    private SettlementService settlementService;

    @Autowired
    private AppealService appealService;

    @Autowired
    private CreditPolicyMapper creditPolicyMapper;

    @Autowired
    private RentalOrderMapper rentalOrderMapper;

    @Autowired
    private SettlementMapper settlementMapper;

    @Autowired
    private OrderOccupancyMapper orderOccupancyMapper;

    @Autowired
    private ItemMapper itemMapper;

    @Autowired
    private LossLogMapper lossLogMapper;

    @Autowired
    private CreditLogMapper creditLogMapper;

    @Autowired
    private AppealMapper appealMapper;

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
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("归还（按时）：订单完成、占用释放、结算单待确认、金额按公式算")
    void return_onTime_createsPendingSettlement() {
        Long itemId = seedItem(OWNER, new BigDecimal("0.0000"));
        Long orderId = seedOrder(itemId, "IN_USE", LocalDate.now().minusDays(1), LocalDate.now().plusDays(2));

        loginAs(OWNER, "张伟", "USER");
        SettlementDetailVO vo = settlementService.returnItem(orderId, new OrderReturnDTO("INTACT", "完好归还"));

        RentalOrder order = rentalOrderMapper.selectById(orderId);
        assertThat(order.getStatus()).isEqualTo("COMPLETED");
        assertThat(order.getActualReturnDate()).isEqualTo(LocalDate.now());
        assertThat(order.getOverdueDays()).isZero();
        assertThat(occupancyRows(orderId)).isZero();

        // 租 3 天 × 20.00 = 60.00；按时无逾期费；帐篷 0.0250 × 3 天系数 0.01 × 完好 1.0 = 0.00025 → 0.0003
        assertThat(vo.rentAmount()).isEqualByComparingTo("60.00");
        assertThat(vo.overdueFee()).isEqualByComparingTo("0.00");
        assertThat(vo.lossRatio()).isEqualByComparingTo("0.0003");
        assertThat(vo.lossFee()).isEqualByComparingTo("0.03");
        assertThat(vo.refundDeposit()).isEqualByComparingTo("99.97");
        assertThat(vo.status()).isEqualTo("PENDING_CONFIRM");
        assertThat(vo.confirmDeadline()).isAfter(LocalDateTime.now().plusHours(47));
        // 按时归还没有 OVERDUE 流水，所以是 租金 / 损耗 / 退还押金 三条
        assertThat(vo.flows()).extracting(SettlementDetailVO.FlowVO::type)
                .containsExactlyInAnyOrder("RENT", "LOSS", "REFUND");

        Item item = itemMapper.selectById(itemId);
        assertThat(item.getStatus()).isEqualTo("ON_SHELF");
        assertThat(item.getLossValue()).isEqualByComparingTo("0.0003");
    }

    @Test
    @DisplayName("归还：承租方不能替出租方标记归还 → 3008")
    void return_byRenter_forbidden() {
        Long itemId = seedItem(OWNER, BigDecimal.ZERO);
        Long orderId = seedOrder(itemId, "IN_USE", LocalDate.now().minusDays(1), LocalDate.now().plusDays(2));

        loginAs(RENTER, "李娜", "USER");
        assertThatThrownBy(() -> settlementService.returnItem(orderId, new OrderReturnDTO("INTACT", null)))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.PERMISSION_DENIED_FOR_RESOURCE.getCode()));
    }

    @Test
    @DisplayName("归还：订单还在待确认 → 3001；归还后重复归还 → 3001")
    void return_wrongState_throws() {
        // 两笔订单必须落在两件不同物品上：同一物品同一日期会撞 uk_occupancy
        Long pendingItemId = seedItem(OWNER, BigDecimal.ZERO);
        Long inUseItemId = seedItem(OWNER, BigDecimal.ZERO);
        Long pendingId = seedOrder(pendingItemId, "PENDING", LocalDate.now(), LocalDate.now().plusDays(2));
        Long inUseId = seedOrder(inUseItemId, "IN_USE", LocalDate.now().minusDays(1), LocalDate.now().plusDays(2));

        loginAs(OWNER, "张伟", "USER");
        assertThatThrownBy(() -> settlementService.returnItem(pendingId, new OrderReturnDTO("INTACT", null)))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.ORDER_STATE_INVALID.getCode()));

        settlementService.returnItem(inUseId, new OrderReturnDTO("INTACT", null));
        assertThatThrownBy(() -> settlementService.returnItem(inUseId, new OrderReturnDTO("INTACT", null)))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.ORDER_STATE_INVALID.getCode()));
    }

    @Test
    @DisplayName("归还（逾期 5 天）：逾期费 = 20 × (3×0.5 + 2×0.5×2) = 70.00，信誉按重逾期扣")
    void return_overdue_feeByTier() {
        Long itemId = seedItem(OWNER, BigDecimal.ZERO);
        Long orderId = seedOrder(itemId, "IN_USE", LocalDate.now().minusDays(10), LocalDate.now().minusDays(5));

        loginAs(OWNER, "张伟", "USER");
        SettlementDetailVO vo = settlementService.returnItem(orderId, new OrderReturnDTO("INTACT", null));

        assertThat(vo.overdueDays()).isEqualTo(5);
        assertThat(vo.overdueFee()).isEqualByComparingTo("70.00");
        // 租 5 天落在 termTier2(≤7 天) 系数 0.0200 → w = 0.0200 × 0.0250 × 1.0 = 0.0005，损耗费 0.05
        assertThat(vo.lossRatio()).isEqualByComparingTo("0.0005");
        assertThat(vo.lossFee()).isEqualByComparingTo("0.05");
        assertThat(vo.refundDeposit()).isEqualByComparingTo("29.95");

        CreditLog penalty = creditLogMapper.selectOne(Wrappers.<CreditLog>lambdaQuery()
                .eq(CreditLog::getOrderId, orderId)
                .eq(CreditLog::getUserId, RENTER));
        assertThat(penalty.getReasonCode()).isEqualTo("OVERDUE_HEAVY");
        assertThat(penalty.getChange()).isEqualTo(-50);
        assertThat(penalty.getBeforeScore()).isEqualTo(penalty.getAfterScore() + 50);
    }

    @Test
    @DisplayName("归还（损坏 + 存量损耗高）：L 越过 0.85，物品转检修中，损耗日志记 REPAIR")
    void return_damagedHighLoss_movesItemToMaintaining() {
        Long itemId = seedItem(OWNER, new BigDecimal("0.8500"));
        Long orderId = seedOrder(itemId, "IN_USE", LocalDate.now().minusDays(1), LocalDate.now().plusDays(2));

        loginAs(OWNER, "张伟", "USER");
        SettlementDetailVO vo = settlementService.returnItem(orderId, new OrderReturnDTO("DAMAGED", "外壳裂了"));

        Item item = itemMapper.selectById(itemId);
        assertThat(item.getStatus()).isEqualTo("MAINTAINING");
        assertThat(item.getLossValue()).isEqualByComparingTo("0.8508");
        assertThat(vo.action()).isEqualTo("REPAIR");
        assertThat(vo.returnState()).isEqualTo("DAMAGED");
        assertThat(vo.lossBefore()).isEqualByComparingTo("0.8500");
        assertThat(vo.lossAfter()).isEqualByComparingTo("0.8508");

        LossLog log = lossLogMapper.selectOne(Wrappers.<LossLog>lambdaQuery().eq(LossLog::getOrderId, orderId));
        assertThat(log.getWeight()).isEqualByComparingTo("0.0008");
        assertThat(log.getReturnCoef()).isEqualByComparingTo("3.0000");
        assertThat(log.getAction()).isEqualTo("REPAIR");

        // 损坏额外扣 10 分
        CreditLog damage = creditLogMapper.selectOne(Wrappers.<CreditLog>lambdaQuery()
                .eq(CreditLog::getOrderId, orderId)
                .eq(CreditLog::getReasonCode, "DAMAGE"));
        assertThat(damage.getChange()).isEqualTo(-10);
    }

    @Test
    @DisplayName("冷静期：承租方确认无异议 → SETTLED，双方各得一次按时履约加分")
    void confirm_byRenter_settlesAndAwardsCredit() {
        Long itemId = seedItem(OWNER, BigDecimal.ZERO);
        Long orderId = seedOrder(itemId, "IN_USE", LocalDate.now().minusDays(1), LocalDate.now().plusDays(2));

        loginAs(OWNER, "张伟", "USER");
        SettlementDetailVO vo = settlementService.returnItem(orderId, new OrderReturnDTO("INTACT", null));

        loginAs(RENTER, "李娜", "USER");
        settlementService.confirm(vo.id());

        Settlement settlement = settlementMapper.selectById(vo.id());
        assertThat(settlement.getStatus()).isEqualTo("SETTLED");
        assertThat(settlement.getConfirmedAt()).isNotNull();
        assertThat(settlement.getSettledAt()).isNotNull();

        long onTime = creditLogMapper.selectCount(Wrappers.<CreditLog>lambdaQuery()
                .eq(CreditLog::getOrderId, orderId)
                .eq(CreditLog::getReasonCode, ON_TIME));
        assertThat(onTime).isEqualTo(2);
    }

    @Test
    @DisplayName("冷静期：出租方不能替承租方确认 → 3008")
    void confirm_byOwner_forbidden() {
        Long itemId = seedItem(OWNER, BigDecimal.ZERO);
        Long orderId = seedOrder(itemId, "IN_USE", LocalDate.now().minusDays(1), LocalDate.now().plusDays(2));

        loginAs(OWNER, "张伟", "USER");
        SettlementDetailVO vo = settlementService.returnItem(orderId, new OrderReturnDTO("INTACT", null));

        assertThatThrownBy(() -> settlementService.confirm(vo.id()))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.PERMISSION_DENIED_FOR_RESOURCE.getCode()));
    }

    @Test
    @DisplayName("冷静期：承租方提异议 → 结算单 DISPUTED 且生成一条待处理申诉")
    void dispute_marksDisputedAndCreatesAppeal() {
        Long itemId = seedItem(OWNER, BigDecimal.ZERO);
        Long orderId = seedOrder(itemId, "IN_USE", LocalDate.now().minusDays(1), LocalDate.now().plusDays(2));

        loginAs(OWNER, "张伟", "USER");
        SettlementDetailVO vo = settlementService.returnItem(orderId, new OrderReturnDTO("WORN", null));

        loginAs(RENTER, "李娜", "USER");
        settlementService.dispute(vo.id(), new SettlementDisputeDTO("归还时就有划痕", List.of("/uploads/202611/a.png")));

        Settlement settlement = settlementMapper.selectById(vo.id());
        assertThat(settlement.getStatus()).isEqualTo("DISPUTED");
        assertThat(settlement.getDisputedAt()).isNotNull();

        Appeal appeal = appealMapper.selectOne(Wrappers.<Appeal>lambdaQuery().eq(Appeal::getOrderId, orderId));
        assertThat(appeal.getStatus()).isEqualTo("PENDING");
        assertThat(appeal.getApplicantId()).isEqualTo(RENTER);
        assertThat(appeal.getEvidenceUrls()).isEqualTo("/uploads/202611/a.png");
    }

    @Test
    @DisplayName("冷静期：超时未表态被扫描任务自动确认，未超时的保持待确认")
    void scanConfirmTimeout_onlyExpired() {
        // 同理：两笔订单用两件不同物品，避免 uk_occupancy 冲突
        Long expiredItemId = seedItem(OWNER, BigDecimal.ZERO);
        Long freshItemId = seedItem(OWNER, BigDecimal.ZERO);
        Long expiredOrderId = seedOrder(expiredItemId, "IN_USE", LocalDate.now().minusDays(1), LocalDate.now().plusDays(2));
        Long freshOrderId = seedOrder(freshItemId, "IN_USE", LocalDate.now().minusDays(1), LocalDate.now().plusDays(2));

        loginAs(OWNER, "张伟", "USER");
        SettlementDetailVO expired = settlementService.returnItem(expiredOrderId, new OrderReturnDTO("INTACT", null));
        SettlementDetailVO fresh = settlementService.returnItem(freshOrderId, new OrderReturnDTO("INTACT", null));

        Settlement patch = new Settlement();
        patch.setId(expired.id());
        patch.setConfirmDeadline(LocalDateTime.now().minusHours(1));
        settlementMapper.updateById(patch);

        int updated = settlementService.scanConfirmTimeout();

        assertThat(updated).isEqualTo(1);
        assertThat(settlementMapper.selectById(expired.id()).getStatus()).isEqualTo("SETTLED");
        assertThat(settlementMapper.selectById(fresh.id()).getStatus()).isEqualTo("PENDING_CONFIRM");
    }

    @Test
    @DisplayName("裁决：支持承租方 → 免损耗费重算应退，结算单转 ADJUSTED")
    void verdict_accepted_adjustsSettlement() {
        Long itemId = seedItem(OWNER, BigDecimal.ZERO);
        Long orderId = seedOrder(itemId, "IN_USE", LocalDate.now().minusDays(10), LocalDate.now().minusDays(5));

        loginAs(OWNER, "张伟", "USER");
        SettlementDetailVO vo = settlementService.returnItem(orderId, new OrderReturnDTO("DAMAGED", null));

        loginAs(RENTER, "李娜", "USER");
        settlementService.dispute(vo.id(), new SettlementDisputeDTO("本来就有裂缝", null));

        Appeal appeal = appealMapper.selectOne(Wrappers.<Appeal>lambdaQuery().eq(Appeal::getOrderId, orderId));
        loginAs(1L, "平台管理员", "ADMIN");
        appealService.verdict(appeal.getId(), new AppealVerdictDTO(VerdictType.RENTER_WIN, null, null, "确有旧裂痕，损耗费免除"));

        Appeal after = appealMapper.selectById(appeal.getId());
        assertThat(after.getStatus()).isEqualTo("ACCEPTED");
        assertThat(after.getAdminId()).isEqualTo(1L);
        assertThat(after.getVerdictType()).isEqualTo(VerdictType.RENTER_WIN);

        Settlement settlement = settlementMapper.selectById(vo.id());
        assertThat(settlement.getStatus()).isEqualTo("ADJUSTED");
        assertThat(settlement.getLossFee()).isEqualByComparingTo("0.00");
        // 押金 100 − 逾期费 70 = 30.00
        assertThat(settlement.getRefundDeposit()).isEqualByComparingTo("30.00");

        loginAs(STRANGER, "王强", "USER");
        assertThatThrownBy(() -> appealService.verdict(appeal.getId(), new AppealVerdictDTO(VerdictType.REJECTED, null, null, "重复裁决")))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.APPEAL_STATE_INVALID.getCode()));
    }

    // ------------------------------------------------------------------ 方案 B：承租方发起归还申请

    @Test
    @DisplayName("归还申请：承租方登记申报状态，订单仍在使用中、占用不释放、不生成结算单")
    void requestReturn_byRenter_recordsTimestampAndKeepsState() {
        Long itemId = seedItem(OWNER, BigDecimal.ZERO);
        Long orderId = seedOrder(itemId, "IN_USE", LocalDate.now().minusDays(1), LocalDate.now().plusDays(2));

        loginAs(RENTER, "李娜", "USER");
        settlementService.requestReturn(orderId, new OrderReturnDTO("WORN", "有点磨损"));

        RentalOrder order = rentalOrderMapper.selectById(orderId);
        assertThat(order.getStatus()).isEqualTo("IN_USE");
        assertThat(order.getReturnRequestedAt()).isNotNull();
        assertThat(order.getReturnRequestedBy()).isEqualTo("RENTER");
        assertThat(order.getReturnRequestedState()).isEqualTo("WORN");
        assertThat(order.getRemark()).isEqualTo("有点磨损");
        // 物品还在承租方手上：占用不能提前释放
        assertThat(occupancyRows(orderId)).isPositive();
        assertThat(settlementMapper.selectCount(Wrappers.<Settlement>lambdaQuery()
                .eq(Settlement::getOrderId, orderId))).isZero();
    }

    @Test
    @DisplayName("归还申请：出租方发起 → 3008")
    void requestReturn_byOwner_forbidden() {
        Long itemId = seedItem(OWNER, BigDecimal.ZERO);
        Long orderId = seedOrder(itemId, "IN_USE", LocalDate.now().minusDays(1), LocalDate.now().plusDays(2));

        loginAs(OWNER, "张伟", "USER");
        assertThatThrownBy(() -> settlementService.requestReturn(orderId, new OrderReturnDTO("INTACT", null)))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.PERMISSION_DENIED_FOR_RESOURCE.getCode()));
    }

    @Test
    @DisplayName("归还申请：重复发起 → 3001")
    void requestReturn_twice_stateInvalid() {
        Long itemId = seedItem(OWNER, BigDecimal.ZERO);
        Long orderId = seedOrder(itemId, "IN_USE", LocalDate.now().minusDays(1), LocalDate.now().plusDays(2));

        loginAs(RENTER, "李娜", "USER");
        settlementService.requestReturn(orderId, new OrderReturnDTO("INTACT", null));
        assertThatThrownBy(() -> settlementService.requestReturn(orderId, new OrderReturnDTO("INTACT", null)))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.ORDER_STATE_INVALID.getCode()));
    }

    @Test
    @DisplayName("归还申请后出租方验收：以出租方确认的状态为准（覆盖申报值）")
    void confirmAfterRequest_usesOwnerState() {
        Long itemId = seedItem(OWNER, BigDecimal.ZERO);
        Long orderId = seedOrder(itemId, "IN_USE", LocalDate.now().minusDays(1), LocalDate.now().plusDays(2));

        loginAs(RENTER, "李娜", "USER");
        settlementService.requestReturn(orderId, new OrderReturnDTO("DAMAGED", "我觉得摔了一下"));

        loginAs(OWNER, "张伟", "USER");
        SettlementDetailVO vo = settlementService.returnItem(orderId, new OrderReturnDTO("INTACT", "验收完好"));

        // 完好：0.0100 × 0.0250 × 1.0 = 0.00025 → 0.0003
        assertThat(vo.lossRatio()).isEqualByComparingTo("0.0003");
        assertThat(vo.lossFee()).isEqualByComparingTo("0.03");
        assertThat(vo.remark()).isEqualTo("验收完好");
        assertThat(rentalOrderMapper.selectById(orderId).getStatus()).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("归还申请超时：出租方不确认 → 按承租方申报状态自动结算")
    void scanReturnTimeout_autoSettlesWithRenterState() {
        Long itemId = seedItem(OWNER, BigDecimal.ZERO);
        Long orderId = seedOrder(itemId, "IN_USE", LocalDate.now().minusDays(1), LocalDate.now().plusDays(2));

        loginAs(RENTER, "李娜", "USER");
        settlementService.requestReturn(orderId, new OrderReturnDTO("WORN", "已经还回去了"));
        // 把申请时间改到 25 小时前（窗口默认 24 小时）—— 只能改时间，不改状态
        RentalOrder patch = new RentalOrder();
        patch.setId(orderId);
        patch.setReturnRequestedAt(LocalDateTime.now().minusHours(25));
        rentalOrderMapper.updateById(patch);

        int updated = settlementService.scanReturnTimeout();
        assertThat(updated).isEqualTo(1);

        RentalOrder order = rentalOrderMapper.selectById(orderId);
        assertThat(order.getStatus()).isEqualTo("COMPLETED");
        assertThat(occupancyRows(orderId)).isZero();

        Settlement settlement = settlementMapper.selectOne(Wrappers.<Settlement>lambdaQuery()
                .eq(Settlement::getOrderId, orderId));
        assertThat(settlement).isNotNull();
        assertThat(settlement.getStatus()).isEqualTo("PENDING_CONFIRM");
        assertThat(settlement.getConfirmDeadline()).isNotNull();
        // 按承租方申报的 WORN：0.0100 × 0.0250 × 1.5 = 0.000375 → 0.0004，损耗费 0.04
        assertThat(settlement.getLossRatio()).isEqualByComparingTo("0.0004");
        assertThat(settlement.getLossFee()).isEqualByComparingTo("0.04");
        assertThat(settlement.getRemark()).contains("超时");
    }

    @Test
    @DisplayName("异议：出租方也能提（超时自动结算后需要有申诉入口）")
    void dispute_byOwner_allowed() {
        Long itemId = seedItem(OWNER, BigDecimal.ZERO);
        Long orderId = seedOrder(itemId, "IN_USE", LocalDate.now().minusDays(1), LocalDate.now().plusDays(2));

        loginAs(OWNER, "张伟", "USER");
        SettlementDetailVO vo = settlementService.returnItem(orderId, new OrderReturnDTO("INTACT", null));

        settlementService.dispute(vo.id(), new SettlementDisputeDTO("承租方申报的损耗不认可", null));

        Settlement settlement = settlementMapper.selectById(vo.id());
        assertThat(settlement.getStatus()).isEqualTo("DISPUTED");
        Appeal appeal = appealMapper.selectOne(Wrappers.<Appeal>lambdaQuery()
                .eq(Appeal::getOrderId, orderId));
        assertThat(appeal).isNotNull();
        assertThat(appeal.getApplicantId()).isEqualTo(OWNER);
    }

    // ------------------------------------------------------------------ 工具

    private Long seedItem(long ownerId, BigDecimal lossValue) {
        Item item = new Item();
        item.setOwnerId(ownerId);
        item.setCategoryId(8L);
        item.setCommunityId(1L);
        item.setTitle("结算测试物品");
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

    private Long seedOrder(Long itemId, String status, LocalDate start, LocalDate end) {
        RentalOrder order = new RentalOrder();
        order.setOrderNo("T" + System.nanoTime());
        order.setItemId(itemId);
        order.setOwnerId(OWNER);
        order.setRenterId(RENTER);
        order.setStartDate(start);
        order.setEndDate(end);
        order.setRentDays((int) java.time.temporal.ChronoUnit.DAYS.between(start, end));
        order.setStatus(status);
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

    private long occupancyRows(Long orderId) {
        return orderOccupancyMapper.selectCount(Wrappers.<OrderOccupancy>lambdaQuery()
                .eq(OrderOccupancy::getOrderId, orderId));
    }

    private void loginAs(Long userId, String nickname, String role) {
        LoginUser user = new LoginUser(userId, nickname, role);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                user, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }
}
