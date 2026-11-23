package com.duyikang.rental.module.admin.vo;

import java.math.BigDecimal;
import java.util.List;

/**
 * 管理端趋势（{@code GET /api/admin/stats/trend?days=7}）。
 *
 * <p>`points` 按日期升序、**空天补 0**、最后一天是今天 —— 前端可以直接把 `points` 丢给 ECharts，
 * 不用自己补洞（补洞由后端统一做，避免两个端算出不同的点）。</p>
 */
public record StatsTrendVO(Integer days, List<PointVO> points) {

    /**
     * @param date       日期（`yyyy-MM-dd`）
     * @param orderCount 当天创建的订单数
     * @param gmv        当天创建的已完成订单租金合计
     * @param newUsers   当天新注册用户数
     */
    public record PointVO(String date, long orderCount, BigDecimal gmv, long newUsers) {
    }
}
