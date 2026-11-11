package com.duyikang.rental.module.item.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 物品图片实体。id 同时是展示顺序（不做 sortNo：多图排序属 P2）。uk_item_image(item_id, url) 拦住重复上传同一张图。
 */
@Data
@TableName("item_image")
public class ItemImage {

    /**
     * 主键（同时也是展示顺序）
     */
    @TableId(type = IdType.AUTO)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 物品
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long itemId;

    /**
     * 相对路径，如 /uploads/202611/abc.jpg
     */
    private String url;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

}
