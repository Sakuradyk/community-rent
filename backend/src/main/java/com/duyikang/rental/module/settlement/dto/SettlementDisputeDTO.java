package com.duyikang.rental.module.settlement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 承租方对结算单提出异议（K3 冷静期的出口之一）。
 *
 * <p>提异议后结算单转 `DISPUTED` 并同事务生成一条申诉，等待管理员裁决。
 * `evidenceUrls` 复用图片上传接口返回的相对路径。</p>
 *
 * @param reason       异议理由（必填，≤500 字，会存进 appeal.reason）
 * @param evidenceUrls 凭证图片（选填，逗号拼接后存进 appeal.evidence_urls）
 */
public record SettlementDisputeDTO(
        @NotBlank(message = "请填写异议理由")
        @Size(max = 500, message = "异议理由最多 500 字")
        String reason,

        List<String> evidenceUrls
) {
}
