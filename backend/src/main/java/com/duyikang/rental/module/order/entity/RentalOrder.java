package com.duyikang.rental.module.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 订单实体。表名用 rental_order 而非 order（order 是 SQL 保留字）。租期是半开区间 [startDate, endDate)，10-01~10-04 表示占用 10-01/10-02/10-03 共 3 天。
 */
@Data
@TableName("rental_order")
public class RentalOrder {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 业务单号，如 R202611110001
     */
    private String orderNo;

    /**
     * 物品
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long itemId;

    /**
     * 出租方（下单时从 item.ownerId 冗余快照，便于双边查询）
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long ownerId;

    /**
     * 承租方
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long renterId;

    /**
     * 起租日（含）
     */
    private LocalDate startDate;

    /**
     * 归还日（不含）
     */
    private LocalDate endDate;

    /**
     * 计费天数 = DATEDIFF(endDate, startDate)，不足 1 天按 1 天
     */
    private Integer rentDays;

    /**
     * 实际归还日
     */
    private LocalDate actualReturnDate;

    /**
     * PENDING / CONFIRMED / IN_USE / OVERDUE / COMPLETED / CANCELLED
     */
    private String status;

    /**
     * 价格快照：日租金
     */
    private BigDecimal dailyPrice;

    /**
     * 价格快照：押金
     */
    private BigDecimal deposit;

    /**
     * 价格快照：逾期费率（×日租金/天）
     */
    private BigDecimal overdueRate;

    /**
     * 租金 = dailyPrice × rentDays（下单时算出并冻结）
     */
    private BigDecimal rentAmount;

    /**
     * 逾期天数（结算时写）
     */
    private Integer overdueDays;

    /**
     * 取消原因
     */
    private String cancelReason;

    /**
     * RENTER / OWNER / ADMIN
     */
    private String cancelBy;

    /**
     * 备注
     */
    private String remark;

    /**
     * 确认时间
     */
    private LocalDateTime confirmedAt;

    /**
     * 开始使用时间
     */
    private LocalDateTime inUseAt;

    /**
     * 完成时间
     */
    private LocalDateTime completedAt;

    /**
     * 取消时间
     */
    private LocalDateTime cancelledAt;

    /**
     * 下单时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

}
