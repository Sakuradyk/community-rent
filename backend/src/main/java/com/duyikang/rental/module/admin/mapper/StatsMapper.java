package com.duyikang.rental.module.admin.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 管理端看板用的只读聚合查询。
 *
 * <p>为什么单独放一个 Mapper 而不用 Wrapper 拼：这些都是 {@code SUM/COUNT/GROUP BY}，
 * MyBatis-Plus 的 Wrapper 表达不了聚合，硬拼字符串又会把表名/列名散在 Service 里。
 * 全部用 {@code #{} } 占位，没有字符串拼接。</p>
 */
@Mapper
public interface StatsMapper {

    /** GMV = 已完成订单的租金合计（不含押金，押金是应退不是收入）。 */
    @Select("SELECT COALESCE(SUM(rent_amount), 0) FROM rental_order WHERE status = 'COMPLETED'")
    BigDecimal sumCompletedRent();

    @Select("SELECT COALESCE(SUM(rent_amount), 0) FROM rental_order "
            + "WHERE status = 'COMPLETED' AND created_at >= #{from}")
    BigDecimal sumCompletedRentSince(@Param("from") LocalDateTime from);

    /** 启用用户的平均信誉分（软删除的排除，禁用账号不拉低平均）。 */
    @Select("SELECT COALESCE(AVG(credit_score), 0) FROM `user` WHERE deleted = 0 AND status = 'NORMAL'")
    BigDecimal averageCreditScore();

    /** 每个状态的订单数：`[{status=PENDING, c=1}, ...]`。 */
    @Select("SELECT status AS status, COUNT(*) AS c FROM rental_order GROUP BY status")
    List<Map<String, Object>> countOrdersByStatus();

    /** 按天统计订单数与 GMV：`[{d=2026-11-20, c=2, g=75.00}, ...]`，字典序即日期序。 */
    @Select("SELECT DATE(created_at) AS d, COUNT(*) AS c, "
            + "COALESCE(SUM(CASE WHEN status = 'COMPLETED' THEN rent_amount ELSE 0 END), 0) AS g "
            + "FROM rental_order WHERE created_at >= #{from} GROUP BY DATE(created_at)")
    List<Map<String, Object>> orderTrend(@Param("from") LocalDateTime from);

    /** 按天统计新注册用户数。 */
    @Select("SELECT DATE(created_at) AS d, COUNT(*) AS c FROM `user` "
            + "WHERE deleted = 0 AND created_at >= #{from} GROUP BY DATE(created_at)")
    List<Map<String, Object>> userTrend(@Param("from") LocalDateTime from);
}
