package com.duyikang.rental.module.item.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 社区字典实体（user / item 共用）。
 *
 * <p>为什么社区要独立成表而不是在 user / item 上存字符串：见 01-schema.sql 中 community 段注释。
 * 一句话：字符串相等比较容不下"阳光花园 / 阳光花园小区"这类别名，字典化后比较退化为整数相等。</p>
 *
 * <p>本表不做逻辑删除：小区停用靠 enabled = 0，绝不物理删除——历史订单、历史物品仍指向它。</p>
 */
@Data
@TableName("community")
public class Community {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 小区名，如 阳光花园
     */
    private String name;

    /**
     * 所属片区/街道，用于排序第二档「同片区」
     */
    private String district;

    /**
     * 所属城市
     */
    private String city;

    /**
     * 别名，英文逗号分隔（仅管理端搜索用，不参与匹配）
     */
    private String alias;

    /**
     * 1 可选 / 0 停用（不再出现在下拉，历史引用保留）
     */
    private Integer enabled;

    /**
     * 下拉展示顺序，越小越前
     */
    private Integer sortNo;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

}
