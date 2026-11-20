package com.duyikang.rental.module.appeal.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.duyikang.rental.common.constant.ErrorCode;
import com.duyikang.rental.common.constant.SettlementStatus;
import com.duyikang.rental.common.exception.BizException;
import com.duyikang.rental.common.result.PageResult;
import com.duyikang.rental.module.appeal.dto.AppealVerdictDTO;
import com.duyikang.rental.module.appeal.entity.Appeal;
import com.duyikang.rental.module.appeal.mapper.AppealMapper;
import com.duyikang.rental.module.appeal.service.AppealService;
import com.duyikang.rental.module.appeal.vo.AppealItemVO;
import com.duyikang.rental.module.item.entity.Item;
import com.duyikang.rental.module.item.mapper.ItemMapper;
import com.duyikang.rental.module.order.entity.RentalOrder;
import com.duyikang.rental.module.order.mapper.RentalOrderMapper;
import com.duyikang.rental.module.settlement.entity.Settlement;
import com.duyikang.rental.module.settlement.mapper.SettlementMapper;
import com.duyikang.rental.module.user.entity.User;
import com.duyikang.rental.module.user.mapper.UserMapper;
import com.duyikang.rental.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 申诉服务实现（K3）。裁决只在 `PENDING` 上生效，重复裁决报 3009。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppealServiceImpl implements AppealService {

    private static final String STATUS_PENDING = "PENDING";

    private static final String STATUS_ACCEPTED = "ACCEPTED";

    private static final String STATUS_REJECTED = "REJECTED";

    private static final int PAGE_SIZE_DEFAULT = 10;

    private static final int PAGE_SIZE_MAX = 100;

    private final AppealMapper appealMapper;

    private final RentalOrderMapper rentalOrderMapper;

    private final ItemMapper itemMapper;

    private final UserMapper userMapper;

    private final SettlementMapper settlementMapper;

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
                    appeal.getVerdict(), appeal.getVerdictTime(), appeal.getCreatedAt());
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
        Settlement settlement = settlementMapper.selectOne(Wrappers.<Settlement>lambdaQuery()
                .eq(Settlement::getOrderId, appeal.getOrderId()));
        if (settlement == null) {
            throw new BizException(ErrorCode.SETTLEMENT_NOT_FOUND);
        }

        LocalDateTime now = LocalDateTime.now();
        boolean accepted = Boolean.TRUE.equals(dto.accepted());

        Appeal appealPatch = new Appeal();
        appealPatch.setId(appeal.getId());
        appealPatch.setStatus(accepted ? STATUS_ACCEPTED : STATUS_REJECTED);
        appealPatch.setVerdict(dto.verdict());
        appealPatch.setAdminId(CurrentUser.requireId());
        appealPatch.setVerdictTime(now);
        appealPatch.setUpdateTime(now);
        appealMapper.updateById(appealPatch);

        Settlement settlePatch = new Settlement();
        settlePatch.setId(settlement.getId());
        if (accepted) {
            // 异议成立：免收损耗费，按"押金 − 逾期费"重算应退
            BigDecimal refund = settlement.getDepositAmount()
                    .subtract(settlement.getOverdueFee())
                    .max(BigDecimal.ZERO)
                    .setScale(2, java.math.RoundingMode.HALF_UP);
            settlePatch.setStatus(SettlementStatus.ADJUSTED);
            settlePatch.setLossFee(BigDecimal.ZERO.setScale(2, java.math.RoundingMode.HALF_UP));
            settlePatch.setRefundDeposit(refund);
            settlePatch.setRemark("申诉通过：" + dto.verdict());
        } else {
            settlePatch.setStatus(SettlementStatus.SETTLED);
            settlePatch.setRemark("申诉驳回：" + dto.verdict());
        }
        settlePatch.setSettledAt(now);
        settlementMapper.updateById(settlePatch);

        log.info("申诉 {} 裁决完成：{}，结算单 {} 转 {}", appeal.getId(),
                accepted ? "通过" : "驳回", settlement.getSettleNo(), settlePatch.getStatus());
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
        return java.util.Arrays.stream(text.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private static String labelOf(String status) {
        return switch (status) {
            case STATUS_PENDING -> "待处理";
            case STATUS_ACCEPTED -> "已支持承租方";
            case STATUS_REJECTED -> "已驳回";
            default -> status;
        };
    }
}
