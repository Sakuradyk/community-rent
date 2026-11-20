package com.duyikang.rental.module.credit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 信誉分变动流水实体（K4）。含前值/后值可完整回溯。uk_credit_biz(user_id, order_id, reason_code) 是幂等键：防定时任务重跑、防连点导致双倍加减分。
 */
@Data
@TableName("credit_log")
public class CreditLog {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 被加减分的用户
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    /**
     * 关联订单（非空：信誉分绑定订单而非角色）
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long orderId;

    /**
     * 变动值，正加负减。
     *
     * <p><b>列名必须用反引号包住</b>：`change` 是 MySQL 保留字，不加反引号时
     * MyBatis-Plus 拼出的 `SELECT change FROM credit_log` 会直接报
     * `BadSqlGrammarException`（语法错误在 change 附近）。实体层的 @TableField
     * 是唯一正确落点，靠在 Wrapper 里手写列名绕不过这条（其它地方仍会踩）。</p>
     */
    @TableField("`change`")
    private Integer change;

    /**
     * 变动前分值
     */
    private Integer beforeScore;

    /**
     * 变动后分值（已截断到 [min,max]）
     */
    private Integer afterScore;

    /**
     * ON_TIME / OVERDUE_LIGHT / OVERDUE_HEAVY / DAMAGE / CANCEL_AFTER_CONFIRM / ADMIN_ADJUST
     */
    private String reasonCode;

    /**
     * 可读说明
     */
    private String reasonDesc;

    /**
     * 发生时间
     */
    private LocalDateTime createdAt;

}
