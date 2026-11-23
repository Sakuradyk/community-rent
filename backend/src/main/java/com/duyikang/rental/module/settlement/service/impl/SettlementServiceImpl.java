package com.duyikang.rental.module.settlement.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.duyikang.rental.common.constant.ErrorCode;
import com.duyikang.rental.common.constant.OrderStatus;
import com.duyikang.rental.common.constant.MessageType;
import com.duyikang.rental.common.constant.ReturnState;
import com.duyikang.rental.common.constant.SettlementStatus;
import com.duyikang.rental.common.exception.BizException;
import com.duyikang.rental.module.appeal.entity.Appeal;
import com.duyikang.rental.module.appeal.mapper.AppealMapper;
import com.duyikang.rental.module.credit.service.CreditService;
import com.duyikang.rental.module.item.entity.Item;
import com.duyikang.rental.module.item.mapper.ItemMapper;
import com.duyikang.rental.module.loss.entity.LossLog;
import com.duyikang.rental.module.loss.mapper.LossLogMapper;
import com.duyikang.rental.module.loss.service.LossService;
import com.duyikang.rental.module.order.entity.OrderOccupancy;
import com.duyikang.rental.module.order.entity.RentalOrder;
import com.duyikang.rental.module.wallet.service.AccountService;
import com.duyikang.rental.module.message.service.MessageService;
import com.duyikang.rental.module.order.mapper.OrderOccupancyMapper;
import com.duyikang.rental.module.order.mapper.RentalOrderMapper;
import com.duyikang.rental.module.settlement.calc.FeeCalculator;
import com.duyikang.rental.module.settlement.dto.OrderReturnDTO;
import com.duyikang.rental.module.settlement.dto.SettlementDisputeDTO;
import com.duyikang.rental.module.settlement.entity.FeeFlow;
import com.duyikang.rental.module.settlement.entity.FeePolicy;
import com.duyikang.rental.module.settlement.entity.Settlement;
import com.duyikang.rental.module.settlement.mapper.FeeFlowMapper;
import com.duyikang.rental.module.settlement.mapper.FeePolicyMapper;
import com.duyikang.rental.module.settlement.mapper.SettlementMapper;
import com.duyikang.rental.module.settlement.service.SettlementService;
import com.duyikang.rental.module.settlement.vo.SettlementDetailVO;
import com.duyikang.rental.module.system.entity.SysConfig;
import com.duyikang.rental.module.system.mapper.SysConfigMapper;
import com.duyikang.rental.security.CurrentUser;
import com.duyikang.rental.security.LoginUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 结算服务实现（K3）。
 *
 * <p>事务边界：归还、确认、异议各自一个事务；逾期费与损耗费全部算完再落库，
 * 任何一步抛异常都整体回滚 —— 不会出现"订单完成了但结算单没生成"的半成品。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementServiceImpl implements SettlementService {

    private static final long POLICY_ID = 1L;

    private static final String CONFIRM_HOURS_KEY = "settlement.confirm.hours";

    private static final long DEFAULT_CONFIRM_HOURS = 48L;

    /**
     * 承租方发起归还申请后，出租方确认的超时窗口（小时）。
     */
    private static final String RETURN_HOURS_KEY = "order.return.confirm.hours";

    private static final long DEFAULT_RETURN_HOURS = 24L;

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final RentalOrderMapper rentalOrderMapper;

    private final SettlementMapper settlementMapper;

    private final FeeFlowMapper feeFlowMapper;

    private final FeePolicyMapper feePolicyMapper;

    private final OrderOccupancyMapper orderOccupancyMapper;

    private final ItemMapper itemMapper;

    private final LossService lossService;

    private final LossLogMapper lossLogMapper;

    private final CreditService creditService;

    private final AppealMapper appealMapper;

    private final SysConfigMapper sysConfigMapper;

    /**
     * 归还申请超时扫描需要"每单一个独立事务"：一单失败不能把其他单一起回滚，
     * 所以这里用编程式事务（TransactionTemplate），而不是在方法上挂 @Transactional。
     */
    private final PlatformTransactionManager transactionManager;
    private final MessageService messageService;
    private final AccountService accountService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SettlementDetailVO returnItem(Long orderId, OrderReturnDTO dto) {
        LoginUser user = CurrentUser.require();
        RentalOrder order = requireOrder(orderId);
        if (!Objects.equals(order.getOwnerId(), user.userId())) {
            throw new BizException(ErrorCode.PERMISSION_DENIED_FOR_RESOURCE, "只有出租方可以标记归还");
        }
        requireReturnableStatus(order);
        requireValidReturnState(dto.returnState());
        // 出租方验收的状态为准；承租方若发起过申请，时间戳保留作为"谁先说还了"的证据
        return doSettle(order, dto.returnState(), dto.remark());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void requestReturn(Long orderId, OrderReturnDTO dto) {
        LoginUser user = CurrentUser.require();
        RentalOrder order = requireOrder(orderId);
        if (!Objects.equals(order.getRenterId(), user.userId())) {
            throw new BizException(ErrorCode.PERMISSION_DENIED_FOR_RESOURCE, "只有承租方可以发起归还申请");
        }
        requireReturnableStatus(order);
        requireValidReturnState(dto.returnState());
        long settled = settlementMapper.selectCount(Wrappers.<Settlement>lambdaQuery()
                .eq(Settlement::getOrderId, orderId));
        if (settled > 0) {
            throw new BizException(ErrorCode.SETTLEMENT_ALREADY_DONE);
        }
        if (order.getReturnRequestedAt() != null) {
            throw new BizException(ErrorCode.ORDER_STATE_INVALID,
                    "已提交过归还申请（" + order.getReturnRequestedAt() + "），请等待出租方确认");
        }
        RentalOrder patch = new RentalOrder();
        patch.setId(order.getId());
        patch.setReturnRequestedAt(LocalDateTime.now());
        patch.setReturnRequestedBy("RENTER");
        patch.setReturnRequestedState(dto.returnState());
        if (dto.remark() != null && !dto.remark().isBlank()) {
            patch.setRemark(dto.remark());
        }
        // 状态保持 IN_USE / OVERDUE：物品还在承租方手上，不能提前释放占用
        rentalOrderMapper.updateById(patch);
        messageService.notify(order.getOwnerId(), MessageType.ORDER, "承租方申报归还，请验收",
                "「" + order.getOrderNo() + "」承租方申报成色为 " + ReturnState.labelOf(dto.returnState())
                        + "，请在 " + returnConfirmHours() + " 小时内验收确认，超时将按申报自动结算。",
                MessageType.BIZ_ORDER, order.getId());

        log.info("订单 {} 承租方发起归还申请（申报状态 {}），出租方需在 {} 小时内确认",
                order.getOrderNo(), dto.returnState(), returnConfirmHours());
    }

    @Override
    public int scanReturnTimeout() {
        long hours = returnConfirmHours();
        LocalDateTime deadline = LocalDateTime.now().minusHours(hours);
        List<RentalOrder> stale = rentalOrderMapper.selectList(Wrappers.<RentalOrder>lambdaQuery()
                .in(RentalOrder::getStatus, List.of(OrderStatus.IN_USE, OrderStatus.OVERDUE))
                .isNotNull(RentalOrder::getReturnRequestedAt)
                .lt(RentalOrder::getReturnRequestedAt, deadline));
        if (stale.isEmpty()) {
            return 0;
        }
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        int done = 0;
        for (RentalOrder order : stale) {
            String state = ReturnState.isValid(order.getReturnRequestedState())
                    ? order.getReturnRequestedState() : ReturnState.INTACT;
            try {
                // 每单一个独立事务：一单失败不影响其他单，也不会留下"完成但没结算"的半成品
                tx.executeWithoutResult(status ->
                        doSettle(order, state, "出租方超时未确认归还，按承租方申报自动结算"));
                done++;
            } catch (RuntimeException e) {
                log.warn("归还申请超时自动结算失败，orderId={}：{}", order.getId(), e.getMessage());
            }
        }
        log.info("归还申请超时扫描：{} 单候选，{} 单自动结算（窗口 {} 小时）", stale.size(), done, hours);
        return done;
    }

    /**
     * 真正干活的那一段：算钱 → 落结算单 → 写流水 → 订单收尾 → 释放占用 → 信誉落账。
     *
     * <p>刻意不带任何权限判断（调用方各自校验），这样出租方确认与超时自动结算可以复用同一段逻辑，
     * 避免两处各算一遍金额。</p>
     */
    private SettlementDetailVO doSettle(RentalOrder order, String returnState, String remark) {
        long settled = settlementMapper.selectCount(Wrappers.<Settlement>lambdaQuery()
                .eq(Settlement::getOrderId, order.getId()));
        if (settled > 0) {
            throw new BizException(ErrorCode.SETTLEMENT_ALREADY_DONE);
        }

        Item item = itemMapper.selectById(order.getItemId());
        if (item == null) {
            throw new BizException(ErrorCode.ITEM_NOT_FOUND);
        }

        LocalDate today = LocalDate.now();
        int overdueDays = FeeCalculator.overdueDays(order, today);
        FeePolicy feePolicy = feePolicyMapper.selectById(POLICY_ID);
        if (feePolicy == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "费用策略未初始化（fee_policy 缺少 id=1）");
        }
        BigDecimal overdueFee = FeeCalculator.overdueFee(order, overdueDays, feePolicy);

        // 损耗核算会写回 item.loss_value 与 item.status（在架 / 检修中）
        LossService.LossResult loss = lossService.apply(item, order, returnState);
        BigDecimal refund = FeeCalculator.refund(order.getDeposit(), overdueFee, loss.lossFee());

        LocalDateTime now = LocalDateTime.now();
        Settlement settlement = new Settlement();
        settlement.setOrderId(order.getId());
        settlement.setSettleNo(nextSettleNo());
        settlement.setRentAmount(order.getRentAmount());
        settlement.setDepositAmount(order.getDeposit());
        settlement.setOverdueDays(overdueDays);
        settlement.setOverdueFee(overdueFee);
        settlement.setLossRatio(loss.weight());
        settlement.setLossFee(loss.lossFee());
        settlement.setRefundDeposit(refund);
        settlement.setStatus(SettlementStatus.PENDING_CONFIRM);
        settlement.setConfirmDeadline(now.plusHours(confirmHours()));
        settlement.setRemark(remark);
        settlement.setCreatedAt(now);
        try {
            settlementMapper.insert(settlement);
        } catch (DuplicateKeyException e) {
            // uk_settle_order 兜底：并发两次归还只有一笔能落库
            throw new BizException(ErrorCode.SETTLEMENT_ALREADY_DONE);
        }

        writeFlows(order, settlement, overdueFee, loss.lossFee(), refund);

        // 订单收尾：完成 + 记录实际归还日与逾期天数
        RentalOrder orderPatch = new RentalOrder();
        orderPatch.setId(order.getId());
        orderPatch.setStatus(OrderStatus.COMPLETED);
        orderPatch.setActualReturnDate(today);
        orderPatch.setOverdueDays(overdueDays);
        rentalOrderMapper.updateById(orderPatch);

        // 账务划转：租金 + 逾期费 + 损耗费归物主，押金剩余退回承租方（复式成对入账）
        accountService.settleOrder(order, order.getRentAmount(), overdueFee, loss.lossFee(), refund);

        // 释放占用：归还后这几天可以再被别人租
        orderOccupancyMapper.delete(Wrappers.<OrderOccupancy>lambdaQuery()
                .eq(OrderOccupancy::getOrderId, order.getId()));

        applyCredit(order, overdueDays, returnState);

        messageService.notify(order.getRenterId(), MessageType.ORDER, "结算单已生成，请确认",
                "「" + order.getOrderNo() + "」已结算：逾期费 " + overdueFee.toPlainString()
                        + " 元、损耗费 " + loss.lossFee().toPlainString() + " 元、应退 " + refund.toPlainString()
                        + " 元。请在冷静期内点「无异议」或「提异议」。",
                MessageType.BIZ_SETTLEMENT, order.getId());

        log.info("订单 {} 归还完成：逾期 {} 天，逾期费 {}，损耗值 {}，损耗费 {}，应退 {}",
                order.getOrderNo(), overdueDays, overdueFee, loss.weight(), loss.lossFee(), refund);
        return buildDetail(settlement, order, returnState,
                loss.lossBefore(), loss.lossAfter(), loss.action());
    }

    @Override
    public SettlementDetailVO detailByOrder(Long orderId) {
        LoginUser user = CurrentUser.require();
        RentalOrder order = requireOrder(orderId);
        if (!CurrentUser.isAdmin()
                && !Objects.equals(order.getOwnerId(), user.userId())
                && !Objects.equals(order.getRenterId(), user.userId())) {
            throw new BizException(ErrorCode.PERMISSION_DENIED_FOR_RESOURCE, "只有订单双方或管理员可以查看结算单");
        }
        Settlement settlement = settlementMapper.selectOne(Wrappers.<Settlement>lambdaQuery()
                .eq(Settlement::getOrderId, orderId));
        if (settlement == null) {
            throw new BizException(ErrorCode.SETTLEMENT_NOT_FOUND);
        }
        LossLog lossLog = lossLogMapper.selectOne(Wrappers.<LossLog>lambdaQuery()
                .eq(LossLog::getOrderId, orderId)
                .orderByDesc(LossLog::getId)
                .last("LIMIT 1"));
        return buildDetail(settlement, order,
                lossLog == null ? null : lossLog.getReturnState(),
                lossLog == null ? null : lossLog.getLossBefore(),
                lossLog == null ? null : lossLog.getLossAfter(),
                lossLog == null ? null : lossLog.getAction());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirm(Long settlementId) {
        Settlement settlement = requireSettlement(settlementId);
        RentalOrder order = requireOrder(settlement.getOrderId());
        requireRenter(order);
        if (SettlementStatus.SETTLED.equals(settlement.getStatus())
                || SettlementStatus.ADJUSTED.equals(settlement.getStatus())) {
            throw new BizException(ErrorCode.SETTLEMENT_ALREADY_DONE);
        }
        if (!SettlementStatus.PENDING_CONFIRM.equals(settlement.getStatus())) {
            throw new BizException(ErrorCode.SETTLEMENT_ALREADY_DONE, "结算单当前状态是「"
                    + SettlementStatus.labelOf(settlement.getStatus()) + "」，不能再确认");
        }
        LocalDateTime now = LocalDateTime.now();
        Settlement patch = new Settlement();
        patch.setId(settlement.getId());
        patch.setStatus(SettlementStatus.SETTLED);
        patch.setConfirmedAt(now);
        patch.setSettledAt(now);
        settlementMapper.updateById(patch);
        log.info("结算单 {} 承租方确认无异议，押金 {} 退还", settlement.getSettleNo(), settlement.getRefundDeposit());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void dispute(Long settlementId, SettlementDisputeDTO dto) {
        Settlement settlement = requireSettlement(settlementId);
        RentalOrder order = requireOrder(settlement.getOrderId());
        // 双方都能提异议：出租方在"承租方申请、超时自动结算"这条路径上也需要申诉入口
        requireParty(order);
        if (!SettlementStatus.PENDING_CONFIRM.equals(settlement.getStatus())) {
            throw new BizException(ErrorCode.SETTLEMENT_ALREADY_DONE, "结算单当前状态是「"
                    + SettlementStatus.labelOf(settlement.getStatus()) + "」，不能再提异议");
        }
        LocalDateTime now = LocalDateTime.now();
        Settlement patch = new Settlement();
        patch.setId(settlement.getId());
        patch.setStatus(SettlementStatus.DISPUTED);
        patch.setDisputedAt(now);
        settlementMapper.updateById(patch);

        Appeal appeal = new Appeal();
        appeal.setOrderId(order.getId());
        appeal.setApplicantId(CurrentUser.requireId());
        appeal.setReason(dto.reason());
        appeal.setEvidenceUrls(joinEvidence(dto.evidenceUrls()));
        appeal.setStatus("PENDING");
        appeal.setCreatedAt(now);
        appeal.setUpdateTime(now);
        appealMapper.insert(appeal);
        // 通知另一方（提异议的对立面）去关注这单争议
        Long counterparty = appeal.getApplicantId().equals(order.getRenterId())
                ? order.getOwnerId() : order.getRenterId();
        messageService.notify(counterparty, MessageType.ORDER, "结算单被提出异议",
                "「" + order.getOrderNo() + "」的结算单已被对方提出异议，管理员裁决后会通知你结果。",
                MessageType.BIZ_SETTLEMENT, order.getId());

        log.info("结算单 {} 被提出异议（申请人 userId={}），已生成申诉 appealId={}",
                settlement.getSettleNo(), appeal.getApplicantId(), appeal.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int scanConfirmTimeout() {
        LocalDateTime now = LocalDateTime.now();
        List<Settlement> expired = settlementMapper.selectList(Wrappers.<Settlement>lambdaQuery()
                .eq(Settlement::getStatus, SettlementStatus.PENDING_CONFIRM)
                .lt(Settlement::getConfirmDeadline, now));
        for (Settlement settlement : expired) {
            Settlement patch = new Settlement();
            patch.setId(settlement.getId());
            patch.setStatus(SettlementStatus.SETTLED);
            patch.setSettledAt(now);
            settlementMapper.updateById(patch);
        }
        if (!expired.isEmpty()) {
            log.info("冷静期扫描：{} 条结算单超时自动确认", expired.size());
        }
        return expired.size();
    }

    // ------------------------------------------------------------------ 内部方法

    private RentalOrder requireOrder(Long orderId) {
        RentalOrder order = orderId == null ? null : rentalOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }
        return order;
    }

    private Settlement requireSettlement(Long settlementId) {
        Settlement settlement = settlementId == null ? null : settlementMapper.selectById(settlementId);
        if (settlement == null) {
            throw new BizException(ErrorCode.SETTLEMENT_NOT_FOUND);
        }
        return settlement;
    }

    private void requireRenter(RentalOrder order) {
        LoginUser user = CurrentUser.require();
        if (!Objects.equals(order.getRenterId(), user.userId())) {
            throw new BizException(ErrorCode.PERMISSION_DENIED_FOR_RESOURCE, "只有承租方可以确认结算单");
        }
    }

    /**
     * 订单双方（承租方或出租方）之一，用于"提异议"：
     * 出租方在归还申请超时被自动结算后，也需要有地方申诉。
     */
    private void requireParty(RentalOrder order) {
        LoginUser user = CurrentUser.require();
        if (!Objects.equals(order.getRenterId(), user.userId())
                && !Objects.equals(order.getOwnerId(), user.userId())
                && !CurrentUser.isAdmin()) {
            throw new BizException(ErrorCode.PERMISSION_DENIED_FOR_RESOURCE, "只有订单双方可以提异议");
        }
    }

    /**
     * 归还前置状态：只有「使用中 / 已逾期」才能归还（或发起归还申请）。
     */
    private void requireReturnableStatus(RentalOrder order) {
        if (!OrderStatus.IN_USE.equals(order.getStatus()) && !OrderStatus.OVERDUE.equals(order.getStatus())) {
            throw new BizException(ErrorCode.ORDER_STATE_INVALID,
                    "无法归还：需要订单处于「使用中」或「已逾期」，当前是「"
                            + OrderStatus.labelOf(order.getStatus()) + "」");
        }
    }

    private void requireValidReturnState(String returnState) {
        if (!ReturnState.isValid(returnState)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "归还状态只能是：完好 / 轻微磨损 / 损坏");
        }
    }

    /**
     * 结算单号：S + 归还日 + 当日 4 位序号
     */
    private String nextSettleNo() {
        String day = LocalDate.now().format(DAY);
        long todayCount = settlementMapper.selectCount(Wrappers.<Settlement>lambdaQuery()
                .likeRight(Settlement::getSettleNo, "S" + day));
        return "S" + day + String.format("%04d", todayCount + 1);
    }

    /**
     * 四条资金流水：租金 / 逾期费 / 损耗费 / 退款（金额为 0 的不记）
     */
    private void writeFlows(RentalOrder order, Settlement settlement,
                            BigDecimal overdueFee, BigDecimal lossFee, BigDecimal refund) {
        String day = LocalDate.now().format(DAY);
        long todayCount = feeFlowMapper.selectCount(Wrappers.<FeeFlow>lambdaQuery()
                .likeRight(FeeFlow::getFlowNo, "F" + day));
        List<FeeFlow> flows = new ArrayList<>();
        flows.add(flow(settlement, order, "RENT", "IN", order.getRentAmount(), "租金", day, todayCount, 1));
        if (overdueFee.compareTo(BigDecimal.ZERO) > 0) {
            flows.add(flow(settlement, order, "OVERDUE", "IN", overdueFee, "逾期费", day, todayCount, 2));
        }
        if (lossFee.compareTo(BigDecimal.ZERO) > 0) {
            flows.add(flow(settlement, order, "LOSS", "IN", lossFee, "损耗费", day, todayCount, 3));
        }
        flows.add(flow(settlement, order, "REFUND", "OUT", refund, "退还押金", day, todayCount, 4));
        flows.forEach(feeFlowMapper::insert);
    }

    private FeeFlow flow(Settlement settlement, RentalOrder order, String type, String direction,
                         BigDecimal amount, String remark, String day, long todayCount, int index) {
        FeeFlow flow = new FeeFlow();
        flow.setFlowNo("F" + day + String.format("%04d%02d", todayCount + 1, index));
        flow.setOrderId(order.getId());
        flow.setSettlementId(settlement.getId());
        flow.setType(type);
        flow.setDirection(direction);
        flow.setAmount(amount);
        flow.setRemark(remark);
        flow.setCreatedAt(LocalDateTime.now());
        return flow;
    }

    /**
     * 信誉落账：承租方按时 +2 / 逾期按天扣 / 损坏再 -10；出租方按时 +2
     */
    private void applyCredit(RentalOrder order, int overdueDays, String returnState) {
        if (overdueDays == 0) {
            creditService.apply(order.getRenterId(), order.getId(), "ON_TIME", 2, "按时归还，信誉 +2");
            creditService.apply(order.getOwnerId(), order.getId(), "ON_TIME", 2, "物品按时回收，信誉 +2");
        } else if (overdueDays <= 3) {
            creditService.apply(order.getRenterId(), order.getId(), "OVERDUE_LIGHT",
                    -5 * overdueDays, "逾期 " + overdueDays + " 天，扣 " + (5 * overdueDays) + " 分");
        } else {
            creditService.apply(order.getRenterId(), order.getId(), "OVERDUE_HEAVY",
                    -10 * overdueDays, "逾期 " + overdueDays + " 天，扣 " + (10 * overdueDays) + " 分");
        }
        if (ReturnState.DAMAGED.equals(returnState)) {
            creditService.apply(order.getRenterId(), order.getId(), "DAMAGE", -10, "物品损坏，信誉 -10");
        }
    }

    private long confirmHours() {
        SysConfig config = sysConfigMapper.selectOne(
                Wrappers.<SysConfig>lambdaQuery().eq(SysConfig::getConfigKey, CONFIRM_HOURS_KEY));
        if (config == null || config.getConfigValue() == null) {
            return DEFAULT_CONFIRM_HOURS;
        }
        try {
            return Long.parseLong(config.getConfigValue().trim());
        } catch (NumberFormatException e) {
            log.warn("{} 配置不是整数（{}），回退默认 {} 小时", CONFIRM_HOURS_KEY, config.getConfigValue(), DEFAULT_CONFIRM_HOURS);
            return DEFAULT_CONFIRM_HOURS;
        }
    }

    /**
     * 归还申请的超时窗口（小时）：读 `sys_config[order.return.confirm.hours]`，缺失或脏数据回退 24。
     */
    private long returnConfirmHours() {
        SysConfig config = sysConfigMapper.selectOne(
                Wrappers.<SysConfig>lambdaQuery().eq(SysConfig::getConfigKey, RETURN_HOURS_KEY));
        if (config == null || config.getConfigValue() == null) {
            return DEFAULT_RETURN_HOURS;
        }
        try {
            return Long.parseLong(config.getConfigValue().trim());
        } catch (NumberFormatException e) {
            log.warn("{} 配置不是整数（{}），回退默认 {} 小时", RETURN_HOURS_KEY, config.getConfigValue(), DEFAULT_RETURN_HOURS);
            return DEFAULT_RETURN_HOURS;
        }
    }

    private String joinEvidence(List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return null;
        }
        String joined = String.join(",", urls.stream().filter(Objects::nonNull).toList());
        return joined.length() > 1000 ? joined.substring(0, 1000) : joined;
    }

    private SettlementDetailVO buildDetail(Settlement settlement, RentalOrder order, String returnState,
                                          BigDecimal lossBefore, BigDecimal lossAfter, String action) {
        LoginUser user = CurrentUser.optional().orElse(null);
        boolean isRenter = user != null && Objects.equals(order.getRenterId(), user.userId());
        boolean isParty = user != null && (isRenter || Objects.equals(order.getOwnerId(), user.userId()));
        boolean withinWindow = settlement.getConfirmDeadline() == null
                || LocalDateTime.now().isBefore(settlement.getConfirmDeadline());
        boolean pending = SettlementStatus.PENDING_CONFIRM.equals(settlement.getStatus());
        List<SettlementDetailVO.FlowVO> flows = feeFlowMapper.selectList(Wrappers.<FeeFlow>lambdaQuery()
                        .eq(FeeFlow::getSettlementId, settlement.getId())
                        .orderByAsc(FeeFlow::getId))
                .stream()
                .map(f -> new SettlementDetailVO.FlowVO(f.getType(), f.getDirection(), f.getAmount(), f.getRemark()))
                .toList();
        // 确认只留给承租方（出租方点确认等于自己给自己背书）；提异议双方都可以
        boolean canConfirm = isRenter && pending && withinWindow;
        boolean canDispute = isParty && pending && withinWindow;
        return new SettlementDetailVO(
                settlement.getId(), settlement.getOrderId(), settlement.getSettleNo(),
                settlement.getRentAmount(), settlement.getDepositAmount(), settlement.getOverdueDays(),
                settlement.getOverdueFee(), settlement.getLossRatio(), settlement.getLossFee(),
                settlement.getRefundDeposit(), settlement.getStatus(), SettlementStatus.labelOf(settlement.getStatus()),
                settlement.getConfirmDeadline(), settlement.getConfirmedAt(), settlement.getDisputedAt(),
                settlement.getSettledAt(), settlement.getRemark(), settlement.getCreatedAt(),
                canConfirm, canDispute,
                lossBefore, lossAfter, returnState, action,
                flows);
    }
}