package com.duyikang.rental.module.item.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 审核入参。
 *
 * <p>{@code approved = false} 时 {@code remark} 必填（驳回不写理由，归属人无法知道要改什么），
 * 这条跨字段规则在 Service 里判——Bean Validation 的字段级注解表达不了"条件必填"。</p>
 */
public record ItemAuditDTO(

        @NotNull(message = "请指定通过或驳回")
        Boolean approved,

        @Size(max = 255, message = "审核意见最多 255 字")
        String remark
) {
}
