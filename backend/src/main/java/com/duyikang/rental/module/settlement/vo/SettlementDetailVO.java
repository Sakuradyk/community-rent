package com.duyikang.rental.module.settlement.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 结算单详情（K3）。前端只读展示 + 两个动作按钮，金额一律由后端算。
 *
 * @param id              结算单 id
 * @param orderId         订单 id
 * @param settleNo        结算单号
 * @param rentAmount      租金
 * @param depositAmount   押金（订单快照）
 * @param overdueDays     逾期天数
 * @param overdueFee      逾期费
 * @param lossRatio       本次损耗值 w
 * @param lossFee         损耗费
 * @param refundDeposit   应退押金
 * @param status          结算单状态
 * @param statusLabel     状态中文名
 * @param confirmDeadline 冷静期截止
 * @param confirmedAt     承租方确认时间
 * @param disputedAt      承租方提异议时间
 * @param settledAt       结算生效时间
 * @param remark          备注
 * @param createdAt       创建时间
 * @param canConfirm      当前用户能否点「无异议」（后端算好，前端别自己算）
 * @param canDispute      当前用户能否点「提异议」
 * @param lossBefore      归还前累计损耗 L
 * @param lossAfter       归还后累计损耗 L
 * @param returnState     归还状态
 * @param action          处置动作 NONE / MAINTAIN / REPAIR
 * @param flows           资金流水
 */
public record SettlementDetailVO(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        @JsonSerialize(using = ToStringSerializer.class) Long orderId,
        String settleNo,
        BigDecimal rentAmount,
        BigDecimal depositAmount,
        Integer overdueDays,
        BigDecimal overdueFee,
        BigDecimal lossRatio,
        BigDecimal lossFee,
        BigDecimal refundDeposit,
        String status,
        String statusLabel,
        LocalDateTime confirmDeadline,
        LocalDateTime confirmedAt,
        LocalDateTime disputedAt,
        LocalDateTime settledAt,
        String remark,
        LocalDateTime createdAt,
        Boolean canConfirm,
        Boolean canDispute,
        BigDecimal lossBefore,
        BigDecimal lossAfter,
        String returnState,
        String action,
        List<FlowVO> flows
) {

    /**
     * 一条资金流水
     *
     * @param type      流水类型 RENT / DEPOSIT / OVERDUE / LOSS / REFUND
     * @param direction 方向 IN 收取 / OUT 支出
     * @param amount    金额
     * @param remark    说明
     */
    public record FlowVO(String type, String direction, BigDecimal amount, String remark) {
    }
}
