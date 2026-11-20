package com.duyikang.rental.module.appeal.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理端申诉列表项（K3）。把「谁对哪一单、为什么不服、当前金额」放在一行里，管理员不用点进去就能判断。
 */
public record AppealItemVO(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        @JsonSerialize(using = ToStringSerializer.class) Long orderId,
        String orderNo,
        String itemTitle,
        @JsonSerialize(using = ToStringSerializer.class) Long applicantId,
        String applicantNickname,
        String reason,
        List<String> evidenceUrls,
        String status,
        String statusLabel,
        String settleNo,
        String settlementStatus,
        String settlementStatusLabel,
        BigDecimal lossFee,
        BigDecimal refundDeposit,
        String verdict,
        String verdictType,
        String verdictTypeLabel,
        LocalDateTime verdictTime,
        LocalDateTime createdAt
) {
}
