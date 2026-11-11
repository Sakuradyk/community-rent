package com.duyikang.rental.module.appeal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 纠纷申诉实体。状态单向：PENDING → ACCEPTED / REJECTED。evidenceUrls 为逗号分隔的图片路径。
 */
@Data
@TableName("appeal")
public class Appeal {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 被申诉订单
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long orderId;

    /**
     * 申请人
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long applicantId;

    /**
     * 申诉理由
     */
    private String reason;

    /**
     * 证据图片，逗号分隔
     */
    private String evidenceUrls;

    /**
     * PENDING / ACCEPTED / REJECTED
     */
    private String status;

    /**
     * 裁决结论
     */
    private String verdict;

    /**
     * 裁决人
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long adminId;

    /**
     * 裁决时间
     */
    private LocalDateTime verdictTime;

    /**
     * 申请时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

}
