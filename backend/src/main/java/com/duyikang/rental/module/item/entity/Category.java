package com.duyikang.rental.module.item.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 物品类目实体（两级树）。lossCoef 是随类目走的属性，故放类目上而不进配置表。
 */
@Data
@TableName("category")
public class Category {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 类目名称
     */
    private String name;

    /**
     * 父类目，0 表示一级
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long parentId;

    /**
     * 层级：1 一级 / 2 二级（最多两级）
     */
    private Integer level;

    /**
     * 类目损耗系数，参与 w = 租期系数×类目系数×归还状态系数
     */
    private BigDecimal lossCoef;

    /**
     * 排序值，越小越前
     */
    private Integer sortNo;

    /**
     * NORMAL 启用 / DISABLED 停用
     */
    private String status;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 逻辑删除：0 正常 / 1 已删
     */
    @TableLogic
    private Integer deleted;

}
