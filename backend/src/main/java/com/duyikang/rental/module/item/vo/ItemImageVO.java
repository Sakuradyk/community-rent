package com.duyikang.rental.module.item.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

/**
 * 详情页的一张图。{@code id} 同时是展示顺序（item_image 不做 sort_no 字段）。
 */
public record ItemImageVO(

        @JsonSerialize(using = ToStringSerializer.class)
        Long id,
        String url
) {
}
