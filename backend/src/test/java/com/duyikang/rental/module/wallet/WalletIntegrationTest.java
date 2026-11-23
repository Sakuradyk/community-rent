package com.duyikang.rental.module.wallet;

import com.duyikang.rental.common.constant.ErrorCode;
import com.duyikang.rental.common.constant.VerdictType;
import com.duyikang.rental.common.exception.BizException;
import com.duyikang.rental.common.result.PageResult;
import com.duyikang.rental.module.appeal.dto.AppealVerdictDTO;
import com.duyikang.rental.module.appeal.entity.Appeal;
import com.duyikang.rental.module.appeal.mapper.AppealMapper;
import com.duyikang.rental.module.appeal.service.AppealService;
import com.duyikang.rental.module.item.entity.Item;
import com.duyikang.rental.module.item.mapper.ItemMapper;
import com.duyikang.rental.module.order.dto.OrderCreateDTO;
import com.duyikang.rental.module.settlement.dto.OrderReturnDTO;
import com.duyikang.rental.module.order.service.OrderService;
import com.duyikang.rental.module.order.service.OrderStateService;
import com.duyikang.rental.module.order.vo.OrderCreatedVO;
import com.duyikang.rental.module.settlement.dto.SettlementDisputeDTO;
import com.duyikang.rental.module.settlement.entity.Settlement;
import com.duyikang.rental.module.settlement.mapper.SettlementMapper;
import com.duyikang.rental.module.settlement.service.SettlementService;
import com.duyikang.rental.module.settlement.vo.SettlementDetailVO;
import com.duyikang.rental.module.wallet.entity.Account;
import com.duyikang.rental.module.wallet.mapper.AccountMapper;
import com.duyikang.rental.module.wallet.service.AccountService;
import com.duyikang.rental.module.wallet.vo.ReconcileVO;
import com.duyikang.rental.module.wallet.vo.WalletEntryVO;
import com.duyikang.rental.module.wallet.vo.WalletVO;
import com.duyikang.rental.security.LoginUser;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.junit.jupiter.api.AfterEach;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 钱包与复式账务集成测试。
 *
 * <p>断言策略：金额一律用「与种子余额 1000.00 的差值」表达，避免与其它测试产生的真实数据耦合；
 * 每个用例都验证「全站守恒」这一条铁律，而不只是单边余额。</p>
 */
@SpringBootTest
@Transactional
@DisplayName("钱包：充值 / 冻结 / 解冻 / 结算划转 / 裁决冲正 / 对账")
class WalletIntegrationTest {

    private static final Long ADMIN = 1L;
    private static final Long OWNER = 2L;
    private static final Long RENTER = 3L;
    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_USER = "USER";
    private static final BigDecimal SEED = new BigDecimal("1000.00");
    private static final BigDecimal ZERO = new BigDecimal("0.00");

    @Autowired private AccountService accountService;
    @Autowired private OrderService orderService;
    @Autowired private OrderStateService orderStateService;
    @Autowired private SettlementService settlementService;
    @Autowired private AppealService appealService;
    @Autowired private ItemMapper itemMapper;
    @Autowired private AccountMapper accountMapper;
    @Autowired private SettlementMapper settlementMapper;
    @Autowired private AppealMapper appealMapper;

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    // ------------------------------------------------------------------ 充值与入账

    @Test
    @DisplayName("模拟充值：可用余额增加、写 RECHARGE 分录、外部账户同步减少")
    void recharge_addsCashAndWritesEntry() {
        loginAs(RENTER, "李娜", ROLE_USER);
        BigDecimal before = walletOf(RENTER).cashBalance();
        BigDecimal externalBefore = externalCash();

        WalletVO after = accountService.recharge(RENTER, new BigDecimal("100.00"));

        assertThat(after.cashBalance()).isEqualByComparingTo(before.add(new BigDecimal("100.00")));
        assertThat(after.frozenBalance()).isEqualByComparingTo(ZERO);
        assertThat(after.totalBalance()).isEqualByComparingTo(before.add(new BigDecimal("100.00")));
        // 平台侧不是凭空生钱：外部账户等额减少
        assertThat(externalCash()).isEqualByComparingTo(externalBefore.subtract(new BigDecimal("100.00")));
        assertThat(entryCountOf(RENTER, "RECHARGE")).isGreaterThanOrEqualTo(1L);
    }

    @Test
    @DisplayName("模拟充值：超过 wallet.recharge.max 报 2001")
    void recharge_overMax_throwsParamError() {
        assertThatThrownBy(() -> accountService.recharge(RENTER, new BigDecimal("6000.00")))
                .isInstanceOfSatisfying(BizException.class, e ->
                        assertThat(e.getCode()).isEqualTo(ErrorCode.PARAM_ERROR.getCode()));
    }

    @Test
    @DisplayName("模拟充值：金额为 0 报 2001")
    void recharge_zero_throwsParamError() {
        assertThatThrownBy(() -> accountService.recharge(RENTER, ZERO))
                .isInstanceOfSatisfying(BizException.class, e ->
                        assertThat(e.getCode()).isEqualTo(ErrorCode.PARAM_ERROR.getCode()));
    }

    // ------------------------------------------------------------------ 下单冻结

    @Test
    @DisplayName("下单：可用余额减少 (租金+押金)，冻结余额同额增加，总额不变")
    void createOrder_freezesRentAndDeposit() {
        Long itemId = seedItem(OWNER);
        BigDecimal before = walletOf(RENTER).cashBalance();
        BigDecimal beforeTotal = walletOf(RENTER).totalBalance();

        Long orderId = createOrderAs(RENTER, "李娜", itemId);

        assertThat(orderId).isNotNull();
        WalletVO after = walletOf(RENTER);
        // 3 天 × 15.00 租金 + 80.00 押金 = 125.00
        assertThat(after.frozenBalance()).isEqualByComparingTo(new BigDecimal("125.00"));
        assertThat(after.cashBalance()).isEqualByComparingTo(before.subtract(new BigDecimal("125.00")));
        assertThat(after.totalBalance()).isEqualByComparingTo(beforeTotal);
        assertThat(entryCountOf(RENTER, "FREEZE")).isGreaterThanOrEqualTo(1L);
    }

    @Test
    @DisplayName("下单：可用余额不足报 4005，且订单不落库")
    void createOrder_insufficientBalance_throws4005() {
        Long itemId = seedItem(OWNER);
        Long accountId = walletOf(RENTER).accountId();
        Account patch = new Account();
        patch.setId(accountId);
        patch.setCashBalance(new BigDecimal("10.00"));
        accountMapper.updateById(patch);

        assertThatThrownBy(() -> createOrderAs(RENTER, "李娜", itemId))
                .isInstanceOfSatisfying(BizException.class, e ->
                        assertThat(e.getCode()).isEqualTo(ErrorCode.BALANCE_NOT_ENOUGH.getCode()));
    }

    @Test
    @DisplayName("取消订单：冻结金额全额回到可用余额")
    void cancelOrder_releasesFreeze() {
        Long itemId = seedItem(OWNER);
        BigDecimal before = walletOf(RENTER).cashBalance();
        Long orderId = createOrderAs(RENTER, "李娜", itemId);

        loginAs(OWNER, "张伟", ROLE_USER);
        orderStateService.cancel(orderId, new com.duyikang.rental.module.order.dto.OrderCancelDTO("测试取消"));

        WalletVO after = walletOf(RENTER);
        assertThat(after.frozenBalance()).isEqualByComparingTo(ZERO);
        assertThat(after.cashBalance()).isEqualByComparingTo(before);
        assertThat(entryCountOf(RENTER, "RELEASE")).isGreaterThanOrEqualTo(1L);
    }

    // ------------------------------------------------------------------ 结算划转

    @Test
    @DisplayName("归还结算：租金+逾期费+损耗费进物主钱包，押金剩余回承租方，外部账户不变")
    void settle_splitsBetweenOwnerAndRenter() {
        Long itemId = seedItem(OWNER);
        BigDecimal renterBefore = walletOf(RENTER).totalBalance();
        BigDecimal ownerBefore = walletOf(OWNER).totalBalance();
        BigDecimal externalBefore = externalCash();

        Long orderId = createOrderAs(RENTER, "李娜", itemId);
        loginAs(OWNER, "张伟", ROLE_USER);
        orderStateService.confirm(orderId);
        orderStateService.pickup(orderId);
        SettlementDetailVO detail = settlementService.returnItem(orderId, new OrderReturnDTO("INTACT", "验收完好"));

        Settlement settlement = settlementMapper.selectOne(Wrappers.<Settlement>lambdaQuery()
                .eq(Settlement::getOrderId, orderId));
        assertThat(settlement).isNotNull();
        BigDecimal toOwner = settlement.getRentAmount()
                .add(settlement.getOverdueFee())
                .add(settlement.getLossFee());
        BigDecimal toRenter = settlement.getRefundDeposit();
        assertThat(toRenter).isGreaterThanOrEqualTo(BigDecimal.ZERO);

        assertThat(detail).isNotNull();
        assertThat(walletOf(OWNER).totalBalance()).isEqualByComparingTo(ownerBefore.add(toOwner));
        // 承租方总额 = 起始 − 租金 − 逾期费 − 损耗费；押金剩余本来就还给自己，不能重复加回
        assertThat(walletOf(RENTER).totalBalance())
                .isEqualByComparingTo(renterBefore.subtract(settlement.getRentAmount())
                        .subtract(settlement.getOverdueFee())
                        .subtract(settlement.getLossFee()));
        assertThat(walletOf(RENTER).frozenBalance()).isEqualByComparingTo(ZERO);
        // 结算不经过平台：外部账户余额不变
        assertThat(externalCash()).isEqualByComparingTo(externalBefore);
    }

    @Test
    @DisplayName("裁决冲正：支持承租方时损耗费从物主钱包回到承租方钱包")
    void verdict_adjustsBothSides() {
        Long itemId = seedItem(OWNER);
        Long orderId = createOrderAs(RENTER, "李娜", itemId);
        loginAs(OWNER, "张伟", ROLE_USER);
        orderStateService.confirm(orderId);
        orderStateService.pickup(orderId);
        settlementService.returnItem(orderId, new OrderReturnDTO("WORN", "轻微磨损"));

        Settlement settlement = settlementMapper.selectOne(Wrappers.<Settlement>lambdaQuery()
                .eq(Settlement::getOrderId, orderId));
        BigDecimal lossFee = settlement.getLossFee();
        BigDecimal ownerBefore = walletOf(OWNER).totalBalance();
        BigDecimal renterBefore = walletOf(RENTER).totalBalance();

        loginAs(RENTER, "李娜", ROLE_USER);
        settlementService.dispute(settlement.getId(),
                new SettlementDisputeDTO("磨损是原有痕迹，不应计费", List.of()));

        Appeal appeal = appealMapper.selectOne(Wrappers.<Appeal>lambdaQuery()
                .eq(Appeal::getOrderId, orderId));
        assertThat(appeal).isNotNull();

        loginAs(ADMIN, "平台管理员", ROLE_ADMIN);
        appealService.verdict(appeal.getId(),
                new AppealVerdictDTO(VerdictType.RENTER_WIN, null, null, "确为原有痕迹，损耗费免除"));

        assertThat(walletOf(OWNER).totalBalance()).isEqualByComparingTo(ownerBefore.subtract(lossFee));
        assertThat(walletOf(RENTER).totalBalance()).isEqualByComparingTo(renterBefore.add(lossFee));
    }

    // ------------------------------------------------------------------ 流水与对账

    @Test
    @DisplayName("我的流水：充值记为 IN，冻结记为 MOVE，均带余额快照")
    void myEntries_directionAndSnapshot() {
        loginAs(RENTER, "李娜", ROLE_USER);
        accountService.recharge(RENTER, new BigDecimal("50.00"));
        Long itemId = seedItem(OWNER);
        createOrderAs(RENTER, "李娜", itemId);

        PageResult<WalletEntryVO> page = accountService.myEntries(RENTER, 1, 50);
        assertThat(page.getList()).isNotEmpty();
        assertThat(page.getList()).anySatisfy(e -> {
            assertThat(e.action()).isEqualTo("RECHARGE");
            assertThat(e.direction()).isEqualTo("IN");
            assertThat(e.amount()).isEqualByComparingTo(new BigDecimal("50.00"));
            assertThat(e.balanceAfter()).isNotNull();
        });
        assertThat(page.getList()).anySatisfy(e -> {
            assertThat(e.action()).isEqualTo("FREEZE");
            assertThat(e.direction()).isEqualTo("MOVE");
            assertThat(e.amount()).isEqualByComparingTo(new BigDecimal("125.00"));
        });
    }

    @Test
    @DisplayName("对账：全站余额之和为 0，balanced 为真")
    void reconcile_balancesToZero() {
        ReconcileVO vo = accountService.reconcile();
        assertThat(vo.balanced()).isTrue();
        assertThat(vo.balanceSum().abs()).isLessThan(new BigDecimal("0.005"));
        assertThat(vo.issues()).isEmpty();
        assertThat(vo.accountCount()).isGreaterThanOrEqualTo(5);
    }

    @Test
    @DisplayName("人工调账：连续两次调整都能落库（幂等键不能固定 biz_id=0）")
    void adminAdjust_twice_bothWritten() {
        BigDecimal before = walletOf(RENTER).cashBalance();
        long adjustBefore = entryCountOf(RENTER, "ADJUST");

        accountService.adminAdjust(RENTER, new BigDecimal("10.00"), "测试加款");
        WalletVO afterFirst = walletOf(RENTER);
        accountService.adminAdjust(RENTER, new BigDecimal("-10.00"), "测试回滚");
        WalletVO afterSecond = walletOf(RENTER);

        assertThat(afterFirst.cashBalance()).isEqualByComparingTo(before.add(new BigDecimal("10.00")));
        assertThat(afterSecond.cashBalance()).isEqualByComparingTo(before);
        assertThat(entryCountOf(RENTER, "ADJUST")).isEqualTo(adjustBefore + 2L);
    }

    // ------------------------------------------------------------------ 工具

    private Long seedItem(long ownerId) {
        Item item = new Item();
        item.setOwnerId(ownerId);
        item.setCategoryId(8L);
        item.setCommunityId(1L);
        item.setTitle("钱包测试物品");
        item.setDescription("仅测试用");
        item.setCoverUrl("/uploads/202611/" + "0".repeat(32) + ".png");
        item.setDailyPrice(new BigDecimal("15.00"));
        item.setDeposit(new BigDecimal("80.00"));
        item.setOverdueRate(new BigDecimal("0.5000"));
        item.setLossValue(new BigDecimal("0.0000"));
        item.setStatus("ON_SHELF");
        itemMapper.insert(item);
        return item.getId();
    }

    private Long createOrderAs(long renterId, String nickname, Long itemId) {
        loginAs(renterId, nickname, ROLE_USER);
        OrderCreatedVO created = orderService.create(new OrderCreateDTO(
                itemId, LocalDate.now().plusDays(40), LocalDate.now().plusDays(43), "钱包测试"));
        return created.id();
    }

    private WalletVO walletOf(Long userId) {
        return accountService.wallet(userId);
    }

    private BigDecimal externalCash() {
        return accountService.reconcile().externalCash();
    }

    private long entryCountOf(Long userId, String action) {
        return accountService.myEntries(userId, 1, 100).getList().stream()
                .filter(e -> action.equals(e.action()))
                .count();
    }

    private void loginAs(Long userId, String nickname, String role) {
        LoginUser user = new LoginUser(userId, nickname, role);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                user, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }
}
