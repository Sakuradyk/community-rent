package com.duyikang.rental.module.appeal.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.duyikang.rental.common.constant.ErrorCode;
import com.duyikang.rental.common.constant.ItemStatus;
import com.duyikang.rental.common.constant.SettlementStatus;
import com.duyikang.rental.common.constant.VerdictType;
import com.duyikang.rental.common.exception.BizException;
import com.duyikang.rental.common.result.PageResult;
import com.duyikang.rental.module.appeal.dto.AppealVerdictDTO;
import com.duyikang.rental.module.appeal.entity.Appeal;
import com.duyikang.rental.module.appeal.mapper.AppealMapper;
import com.duyikang.rental.module.appeal.service.AppealService;
import com.duyikang.rental.module.appeal.vo.AppealItemVO;
import com.duyikang.rental.module.credit.entity.CreditLog;
import com.duyikang.rental.module.credit.mapper.CreditLogMapper;
import com.duyikang.rental.module.credit.service.CreditService;
import com.duyikang.rental.module.item.entity.Item;
import com.duyikang.rental.module.item.mapper.ItemMapper;
import com.duyikang.rental.module.loss.entity.LossLog;
import com.duyikang.rental.module.loss.entity.LossPolicy;
import com.duyikang.rental.module.loss.mapper.LossLogMapper;
import com.duyikang.rental.module.loss.mapper.LossPolicyMapper;
import com.duyikang.rental.module.order.entity.RentalOrder;
import com.duyikang.rental.module.order.mapper.RentalOrderMapper;
import com.duyikang.rental.module.settlement.entity.FeeFlow;
import com.duyikang.rental.module.settlement.entity.Settlement;
import com.duyikang.rental.module.settlement.mapper.FeeFlowMapper;
import com.duyikang.rental.module.settlement.mapper.SettlementMapper;
import com.duyikang.rental.module.user.entity.User;
import com.duyikang.rental.module.user.mapper.UserMapper;
import com.duyikang.rental.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 申诉服务实现（K3）。
 *
 * <p>裁决只在 `PENDING` 上生效，重复裁决报 3009。四种裁定类型见 {@link VerdictType}——
 * 关键在于<b>双方都能申诉</b>，「支持」对双方的方向相反，所以不能用布尔表达。</p>
 *
 * <p>三条账务纪律（改动本文件必读）：</p>
 * <ol>
 *   <li><b>流水只增不改</b>：裁定改变金额时追加一条 `type=ADJUST` 的冲正流水，
 *       原 `LOSS` / `REFUND` 行保持原样，账才对得上；</li>
 *   <li><b>物品损耗按裁定比例回滚</b>：损耗费降低多少，就从 `item.loss_value` 里扣回对应比例的
 *       本次权重（来自 `loss_log.weight`），并校正 `MAINTAINING` 状态；</li>
 *   <li><b>信誉不回改历史</b>：`credit_log` 是流水账，撤销扣分靠再记一条 `ADMIN_ADJUST`。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppealServiceImpl implements AppealService {

    private static final String STATUS_PENDING = "PENDING";

    private static final String STATUS_ACCEPTED = "ACCEPTED";

    private static final String STATUS_REJECTED = "REJECTED";

    private static final String REASON_DAMAGE = "DAMAGE";

    private static final String REASON_ADMIN_ADJUST = "ADMIN_ADJUST";

    private static final String FLOW_ADJUST = "ADJUST";

    private static final String DIRECTION_IN = "IN";

    private static final String DIRECTION_OUT = "OUT";

    private static final int PAGE_SIZE_DEFAULT = 10;

    private static final int PAGE_SIZE_MAX = 100;

    private static final long POLICY_ID = 1L;

    private static final BigDecimal DEFAULT_REPAIR_THRESHOLD = new BigDecimal("0.8500");

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final AppealMapper appealMapper;

    private final RentalOrderMapper rentalOrderMapper;

    private final ItemMapper itemMapper;

    private final UserMapper userMapper;

    private final SettlementMapper settlementMapper;

    private final FeeFlowMapper feeFlowMapper;

    private final LossLogMapper lossLogMapper;

    private final LossPolicyMapper lossPolicyMapper;

    private final CreditLogMapper creditLogMapper;

    private final CreditService creditService;

    @Override
    public PageResult<AppealItemVO> page(String status, Integer pageNum, Integer pageSize) {
        long page = pageNum == null || pageNum < 1 ? 1 : pageNum;
        long size = pageSize == null || pageSize < 1 ? PAGE_SIZE_DEFAULT : Math.min(pageSize, PAGE_SIZE_MAX);
        String filter = status == null || status.isBlank() ? STATUS_PENDING : status.trim();
        Page<Appeal> result = appealMapper.selectPage(new Page<>(page, size),
                Wrappers.<Appeal>lambdaQuery()
                        .eq(Appeal::getStatus, filter)
                        .orderByAsc(Appeal::getCreatedAt)
                        .orderByAsc(Appeal::getId));
        List<Appeal> appeals = result.getRecords();
        if (appeals.isEmpty()) {
            return PageResult.empty(page, size);
        }

        Map<Long, RentalOrder> orderMap = toMap(
                rentalOrderMapper.selectBatchIds(appeals.stream().map(Appeal::getOrderId).filter(Objects::nonNull).distinct().toList()),
                RentalOrder::getId);
        Map<Long, Item> itemMap = toMap(
                itemMapper.selectBatchIds(orderMap.values().stream().map(RentalOrder::getItemId).distinct().toList()),
                Item::getId);
        Map<Long, User> userMap = toMap(
                userMapper.selectBatchIds(appeals.stream().map(Appeal::getApplicantId).filter(Objects::nonNull).distinct().toList()),
                User::getId);
        Map<Long, Settlement> settleMap = toMap(
                settlementMapper.selectList(Wrappers.<Settlement>lambdaQuery()
                        .in(Settlement::getOrderId, orderMap.keySet())),
                Settlement::getOrderId);

        List<AppealItemVO> list = appeals.stream().map(appeal -> {
            RentalOrder order = orderMap.get(appeal.getOrderId());
            Item item = order == null ? null : itemMap.get(order.getItemId());
            User applicant = userMap.get(appeal.getApplicantId());
            Settlement settlement = settleMap.get(appeal.getOrderId());
            return new AppealItemVO(
                    appeal.getId(), appeal.getOrderId(),
                    order == null ? null : order.getOrderNo(),
                    item == null ? null : item.getTitle(),
                    appeal.getApplicantId(),
                    applicant == null ? null : applicant.getNickname(),
                    appeal.getReason(),
                    splitEvidence(appeal.getEvidenceUrls()),
                    appeal.getStatus(), labelOf(appeal.getStatus()),
                    settlement == null ? null : settlement.getSettleNo(),
                    settlement == null ? null : settlement.getStatus(),
                    settlement == null ? null : SettlementStatus.labelOf(settlement.getStatus()),
                    settlement == null ? BigDecimal.ZERO : settlement.getLossFee(),
                    settlement == null ? BigDecimal.ZERO : settlement.getRefundDeposit(),
                    appeal.getVerdict(), appeal.getVerdictType(),
                    VerdictType.labelOf(appeal.getVerdictType()),
                    appeal.getVerdictTime(), appeal.getCreatedAt());
        }).toList();
        return PageResult.of(list, result.getTotal(), page, size);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void verdict(Long appealId, AppealVerdictDTO dto) {
        Appeal appeal = appealId == null ? null : appealMapper.selectById(appealId);
        if (appeal == null) {
            throw new BizException(ErrorCode.APPEAL_NOT_FOUND);
        }
        if (!STATUS_PENDING.equals(appeal.getStatus())) {
            throw new BizException(ErrorCode.APPEAL_STATE_INVALID);
        }
        String type = dto.verdictType() == null ? null : dto.verdictType().trim();
        if (!VerdictType.isValid(type)) {
            throw new BizException(ErrorCode.PARAM_ERROR,
                    "裁定类型只能是：支持承租方（RENTER_WIN）/ 支持出租方（OWNER_WIN）/ 部分支持（PARTIAL）/ 驳回（REJECTED）");
        }
        Settlement settlement = settlementMapper.selectOne(Wrappers.<Settlement>lambdaQuery()
                .eq(Settlement::getOrderId, appeal.getOrderId()));
        if (settlement == null) {
            throw new BizException(ErrorCode.SETTLEMENT_NOT_FOUND);
        }
        RentalOrder order = appeal.getOrderId() == null ? null : rentalOrderMapper.selectById(appeal.getOrderId());

        BigDecimal deposit = scale2(nvl(settlement.getDepositAmount()));
        BigDecimal overdueFee = scale2(nvl(settlement.getOverdueFee()));
        BigDecimal originalLossFee = scale2(nvl(settlement.getLossFee()));
        BigDecimal originalRefund = scale2(nvl(settlement.getRefundDeposit()));

        BigDecimal newLossFee;
        String newStatus;
        switch (type) {
            case VerdictType.RENTER_WIN -> {
                newLossFee = scale2(BigDecimal.ZERO);
                newStatus = SettlementStatus.ADJUSTED;
            }
            case VerdictType.OWNER_WIN -> {
                // 损耗成立：按结算单里已经算好的 loss_ratio 恢复损耗费（不再二次累加物品损耗）
                BigDecimal ratio = nvl(settlement.getLossRatio());
                newLossFee = scale2(deposit.multiply(ratio)).min(deposit);
                newStatus = SettlementStatus.ADJUSTED;
            }
            case VerdictType.PARTIAL -> {
                if (dto.lossFeeOverride() == null) {
                    throw new BizException(ErrorCode.PARAM_ERROR, "部分支持必须填写裁定损耗金额");
                }
                newLossFee = scale2(dto.lossFeeOverride());
                if (newLossFee.compareTo(originalLossFee) > 0) {
                    throw new BizException(ErrorCode.PARAM_ERROR,
                            "裁定损耗金额不能高于原损耗费 " + originalLossFee.toPlainString());
                }
                newStatus = SettlementStatus.ADJUSTED;
            }
            default -> {
                newLossFee = originalLossFee;
                newStatus = SettlementStatus.SETTLED;
            }
        }
        BigDecimal newRefund = scale2(deposit.subtract(overdueFee).subtract(newLossFee).max(BigDecimal.ZERO));
        BigDecimal delta = newRefund.subtract(originalRefund);

        LocalDateTime now = LocalDateTime.now();

        Appeal appealPatch = new Appeal();
        appealPatch.setId(appeal.getId());
        appealPatch.setStatus(VerdictType.REJECTED.equals(type) ? STATUS_REJECTED : STATUS_ACCEPTED);
        appealPatch.setVerdict(dto.verdict());
        appealPatch.setVerdictType(type);
        appealPatch.setAdminId(CurrentUser.requireId());
        appealPatch.setVerdictTime(now);
        appealPatch.setUpdateTime(now);
        appealMapper.updateById(appealPatch);

        Settlement settlePatch = new Settlement();
        settlePatch.setId(settlement.getId());
        settlePatch.setStatus(newStatus);
        settlePatch.setLossFee(newLossFee);
        settlePatch.setRefundDeposit(newRefund);
        settlePatch.setRemark("裁决（" + VerdictType.labelOf(type) + "）：" + dto.verdict());
        settlePatch.setSettledAt(now);
        settlementMapper.updateById(settlePatch);

        if (delta.signum() != 0) {
            writeAdjustFlow(order, settlement, originalLossFee, newLossFee, originalRefund, newRefund, delta);
        }
        if (newLossFee.compareTo(originalLossFee) < 0) {
            rollbackItemLoss(order, originalLossFee, newLossFee);
        }

        Long renterId = order == null ? null : order.getRenterId();
        if (renterId != null) {
            if (VerdictType.RENTER_WIN.equals(type)) {
                rollbackDamagePenalty(renterId, appeal.getOrderId());
            }
            if (dto.creditAdjust() != null && dto.creditAdjust() != 0) {
                creditService.apply(renterId, appeal.getOrderId(), REASON_ADMIN_ADJUST, dto.creditAdjust(),
                        "管理员裁决调整：" + dto.verdict());
            }
        }

        log.info("申诉 {} 裁决完成：{}，结算单 {} 转 {}，损耗费 {} → {}，应退 {} → {}",
                appeal.getId(), type, settlement.getSettleNo(), newStatus,
                originalLossFee.toPlainString(), newLossFee.toPlainString(),
                originalRefund.toPlainString(), newRefund.toPlainString());
    }

    /**
     * 追加一条冲正流水（`type=ADJUST`）。历史流水不动，账只冲不改。
     */
    private void writeAdjustFlow(RentalOrder order, Settlement settlement,
                                 BigDecimal originalLossFee, BigDecimal newLossFee,
                                 BigDecimal originalRefund, BigDecimal newRefund, BigDecimal delta) {
        String day = LocalDate.now().format(DAY);
        long todayCount = feeFlowMapper.selectCount(Wrappers.<FeeFlow>lambdaQuery()
                .likeRight(FeeFlow::getFlowNo, "F" + day));
        FeeFlow flow = new FeeFlow();
        flow.setFlowNo("F" + day + String.format("%04d%02d", todayCount + 1, 5));
        flow.setOrderId(order == null ? settlement.getOrderId() : order.getId());
        flow.setSettlementId(settlement.getId());
        flow.setType(FLOW_ADJUST);
        flow.setDirection(delta.signum() > 0 ? DIRECTION_OUT : DIRECTION_IN);
        flow.setAmount(scale2(delta.abs()));
        flow.setRemark("裁决冲正：损耗费 " + originalLossFee.toPlainString() + " → " + newLossFee.toPlainString()
                + "，应退 " + originalRefund.toPlainString() + " → " + newRefund.toPlainString());
        flow.setCreatedAt(LocalDateTime.now());
        feeFlowMapper.insert(flow);
    }

    /**
     * 物品累计损耗按裁定比例回滚：损耗费降到原值的 {@code keepRatio} 倍，就从 `item.loss_value`
     * 里扣回 {@code (1 - keepRatio) × 本次权重}。若物品正因这次损耗处于 `MAINTAINING` 且回落到检修阈值下，恢复上架。
     */
    private void rollbackItemLoss(RentalOrder order, BigDecimal originalLossFee, BigDecimal newLossFee) {
        if (order == null || order.getItemId() == null) {
            return;
        }
        Item item = itemMapper.selectById(order.getItemId());
        if (item == null) {
            return;
        }
        LossLog lossLog = lossLogMapper.selectList(Wrappers.<LossLog>lambdaQuery()
                        .eq(LossLog::getOrderId, order.getId())
                        .orderByDesc(LossLog::getId))
                .stream().findFirst().orElse(null);
        if (lossLog == null || lossLog.getWeight() == null) {
            log.warn("物品损耗回滚跳过：订单 {} 没有 loss_log 记录", order.getId());
            return;
        }
        BigDecimal keepRatio = originalLossFee.signum() == 0
                ? BigDecimal.ONE
                : newLossFee.divide(originalLossFee, 4, RoundingMode.HALF_UP);
        BigDecimal rollback = scale4(lossLog.getWeight()
                .multiply(BigDecimal.ONE.subtract(keepRatio)).max(BigDecimal.ZERO));
        BigDecimal lossAfter = scale4(nvl(item.getLossValue()).subtract(rollback).max(BigDecimal.ZERO));

        Item patch = new Item();
        patch.setId(item.getId());
        patch.setLossValue(lossAfter);
        if (ItemStatus.MAINTAINING.equals(item.getStatus()) && lossAfter.compareTo(repairThreshold()) < 0) {
            patch.setStatus(ItemStatus.ON_SHELF);
            log.info("物品 {} 累计损耗回落到 {}，从 MAINTAINING 恢复 ON_SHELF", item.getId(), lossAfter.toPlainString());
        }
        itemMapper.updateById(patch);
        log.info("物品 {} 损耗回滚：{} − {} = {}", item.getId(),
                nvl(item.getLossValue()).toPlainString(), rollback.toPlainString(), lossAfter.toPlainString());
    }

    /**
     * 申诉成立时回滚这次订单的 `DAMAGE` 扣分（只能新增 `ADMIN_ADJUST`，历史不改）
     */
    private void rollbackDamagePenalty(Long renterId, Long orderId) {
        CreditLog damage = creditLogMapper.selectOne(Wrappers.<CreditLog>lambdaQuery()
                .eq(CreditLog::getUserId, renterId)
                .eq(CreditLog::getOrderId, orderId)
                .eq(CreditLog::getReasonCode, REASON_DAMAGE));
        if (damage == null || damage.getChange() == null || damage.getChange() >= 0) {
            return;
        }
        creditService.apply(renterId, orderId, REASON_ADMIN_ADJUST, Math.abs(damage.getChange()),
                "申诉成立，回滚损坏扣分");
    }

    private BigDecimal repairThreshold() {
        LossPolicy policy = lossPolicyMapper.selectById(POLICY_ID);
        if (policy == null || policy.getThresholdRepair() == null) {
            return DEFAULT_REPAIR_THRESHOLD;
        }
        return policy.getThresholdRepair();
    }

    private static BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static BigDecimal scale2(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal scale4(BigDecimal value) {
        return value.setScale(4, RoundingMode.HALF_UP);
    }

    private static <T> Map<Long, T> toMap(List<T> list, Function<T, Long> idOf) {
        if (list == null || list.isEmpty()) {
            return Collections.emptyMap();
        }
        return list.stream().filter(Objects::nonNull).collect(Collectors.toMap(idOf, item -> item, (a, b) -> a));
    }

    private static List<String> splitEvidence(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return Arrays.stream(text.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private static String labelOf(String status) {
        return switch (status) {
            case STATUS_PENDING -> "待处理";
            case STATUS_ACCEPTED -> "已裁决";
            case STATUS_REJECTED -> "已驳回";
            default -> status;
        };
    }
}
