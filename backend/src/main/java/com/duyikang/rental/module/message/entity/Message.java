package com.duyikang.rental.module.message.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 站内消息实体。订单状态变更 / 信誉变动 / 检修提醒的通知，不做实时推送（不做 IM）。
 */
@Data
@TableName("message")
public class Message {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 接收人
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    /**
     * ORDER 订单 / CREDIT 信誉 / MAINTAIN 检修 / SYSTEM 系统
     */
    private String type;

    /**
     * 标题
     */
    private String title;

    /**
     * 正文
     */
    private String content;

    /**
     * 业务类型（用于前端跳转）
     */
    private String bizType;

    /**
     * 业务主键（如 orderId）
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long bizId;

    /**
     * 0 未读 / 1 已读
     */
    private Integer isRead;

    /**
     * 阅读时间
     */
    private LocalDateTime readTime;

    /**
     * 发送时间
     */
    private LocalDateTime createdAt;

    /**
     * 逻辑删除：0 正常 / 1 已删
     */
    @TableLogic
    private Integer deleted;

}
