package com.duyikang.rental.module.item.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * 发布 / 编辑物品的入参（两个接口共用，编辑是整体替换而非局部更新）。
 *
 * <p>这里的注解只做"格式与必填"的粗筛；价格上下限、类目层级、社区是否停用这些**业务校验**
 * 放在 Service 里，因为它们要读配置表与字典表（PROJECT_RULES.md §4.3：Controller 只校验格式）。</p>
 *
 * <p>没有 {@code coverUrl} 字段是刻意的：封面由后端取 {@code imageUrls[0]}，
 * 前端不参与封面决策（docs/接口文档.md §2.0 冻结约定）。</p>
 */
public record ItemSaveDTO(

        @NotBlank(message = "标题不能为空")
        @Size(max = 64, message = "标题最多 64 字")
        String title,

        @NotNull(message = "请选择类目")
        Long categoryId,

        @Size(max = 1000, message = "描述最多 1000 字")
        String description,

        @NotNull(message = "请选择所在社区")
        Long communityId,

        @NotNull(message = "请填写日租金")
        @DecimalMin(value = "0.01", message = "日租金必须大于 0")
        BigDecimal dailyPrice,

        @NotNull(message = "请填写押金")
        @DecimalMin(value = "0.00", message = "押金不能为负")
        BigDecimal deposit,

        @NotNull(message = "请填写逾期费率")
        @DecimalMin(value = "0.0000", message = "逾期费率不能为负")
        BigDecimal overdueRate,

        @NotEmpty(message = "至少上传一张图片")
        @Size(max = 5, message = "最多上传 5 张图片")
        List<@NotBlank(message = "图片地址不能为空") String> imageUrls
) {
}
