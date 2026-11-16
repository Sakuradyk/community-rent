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
 * 物品实体。价格三件套在发布时确定，下单时快照进订单，之后改价不影响历史订单。lossValue 是累计损耗 L ∈ [0,1]。
 */
@Data
@TableName("item")
public class Item {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 物品归属人（与订单 ownerId 语义不同）
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long ownerId;

    /**
     * 类目
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long categoryId;

    /**
     * 标题
     */
    private String title;

    /**
     * 描述（推荐内容特征的来源）
     */
    private String description;

    /**
     * 封面图（= item_image 里 id 最小的一张，冗余存储以免列表页 join）
     */
    private String coverUrl;

    /**
     * 所在社区，引用 community.id（发布时必填）
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long communityId;

    /**
     * 日租金
     */
    private BigDecimal dailyPrice;

    /**
     * 押金
     */
    private BigDecimal deposit;

    /**
     * 逾期费率（×日租金/天）
     */
    private BigDecimal overdueRate;

    /**
     * 累计损耗值 L ∈ [0,1]
     */
    private BigDecimal lossValue;

    /**
     * PENDING 待审 / ON_SHELF 在架 / OFF_SHELF 下架 / RENTED 租出中 / MAINTAINING 检修中 / REJECTED 审核驳回
     */
    private String status;

    /**
     * 审核意见
     */
    private String auditRemark;

    /**
     * 审核人
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long auditAdminId;

    /**
     * 审核时间
     */
    private LocalDateTime auditTime;

    /**
     * 累计成交次数（热度特征，免去 count(*)）
     */
    private Integer rentCount;

    /**
     * 累计浏览数（热度特征）
     */
    private Integer viewCount;

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
