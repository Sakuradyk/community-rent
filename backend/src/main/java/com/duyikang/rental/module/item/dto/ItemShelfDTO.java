package com.duyikang.rental.module.item.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 上下架入参。
 *
 * <p>用一个布尔而不是两个接口（{@code /on-shelf}、{@code /off-shelf}）：动作只有一对互逆操作，
 * 拆两个接口会让前端多一个"当前状态该调哪个"的判断，而状态机本来就该在后端判。</p>
 */
public record ItemShelfDTO(

        @NotNull(message = "请指定上架或下架")
        Boolean onShelf
) {
}
