package com.duyikang.rental.module.settlement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 归还请求（K3）。只有出租方可以提交，`returnState` 决定损耗系数的第三项。
 *
 * @param returnState 归还状态：INTACT 完好 / WORN 明显磨损 / DAMAGED 损坏
 * @param remark      备注（选填），会写进结算单备注
 */
public record OrderReturnDTO(
        @NotBlank(message = "请选择归还状态")
        String returnState,

        @Size(max = 255, message = "备注最多 255 字")
        String remark
) {
}
