package com.duyikang.rental.module.appeal.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 管理员对申诉的裁决（K3）。
 *
 * <p>裁定类型是一个枚举而不是布尔，因为申诉人可能是<b>双方</b>：</p>
 * <ul>
 *   <li>`RENTER_WIN` 支持承租方 → 损耗费归零，应退 = 押金 − 逾期费；</li>
 *   <li>`OWNER_WIN` 支持出租方 → 损耗费按结算单 `loss_ratio` 恢复；</li>
 *   <li>`PARTIAL` 部分支持 → 用 `lossFeeOverride` 作为新损耗费（0 ≤ x ≤ 原损耗费）；</li>
 *   <li>`REJECTED` 驳回 → 金额不动，结算单按原判结清。</li>
 * </ul>
 *
 * <p>每条裁定都会往 `fee_flow` 追加一条 `ADJUST` 冲正流水（金额有变化时），
 * **历史流水不改**——流水是账，账只能冲不能改。</p>
 *
 * @param verdictType     裁定类型，四个取值见 {@code VerdictType}
 * @param lossFeeOverride 部分支持时的新损耗费（仅 `PARTIAL` 必填）
 * @param creditAdjust    可选：手工调整<b>承租方</b>信誉分（走 `ADMIN_ADJUST` 流水，范围 −50 ~ +50）
 * @param verdict         裁决说明（必填，≤500 字，会展示给双方）
 */
public record AppealVerdictDTO(
        @NotBlank(message = "请选择裁定结果")
        String verdictType,

        @DecimalMin(value = "0.00", message = "裁定损耗金额不能为负")
        BigDecimal lossFeeOverride,

        @Min(value = -50, message = "信誉调整最少 -50")
        @Max(value = 50, message = "信誉调整最多 +50")
        Integer creditAdjust,

        @NotBlank(message = "请填写裁决说明")
        @Size(max = 500, message = "裁决说明最多 500 字")
        String verdict
) {
}
