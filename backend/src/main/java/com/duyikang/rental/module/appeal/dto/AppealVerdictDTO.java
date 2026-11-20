package com.duyikang.rental.module.appeal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 管理员对申诉的裁决（K3）。
 *
 * <p>裁定通过 = 承租方的异议成立：免收损耗费并按新口径重算应退押金，结算单转 `ADJUSTED`；
 * 裁定驳回 = 维持出租方认定，结算单转 `SETTLED`。</p>
 *
 * @param accepted 是否支持承租方的异议
 * @param verdict  裁决说明（必填，会展示给双方看，≤500 字）
 */
public record AppealVerdictDTO(
        @NotNull(message = "请选择裁决结果")
        Boolean accepted,

        @NotBlank(message = "请填写裁决说明")
        @Size(max = 500, message = "裁决说明最多 500 字")
        String verdict
) {
}
