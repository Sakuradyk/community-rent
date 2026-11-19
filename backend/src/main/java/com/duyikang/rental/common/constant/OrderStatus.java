package com.duyikang.rental.common.constant;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 订单状态常量与中文标签。
 *
 * <p>六态在需求总结 §6 已冻结：PENDING / CONFIRMED / IN_USE / OVERDUE / COMPLETED / CANCELLED。
 * 前四态是"活的"（占用时段），COMPLETED 与 CANCELLED 是终态（释放占用）。
 * 与 {@code item.status} 不同，订单状态一旦进入终态不可回退。</p>
 */
public final class OrderStatus {

    public static final String PENDING = "PENDING";
    public static final String CONFIRMED = "CONFIRMED";
    public static final String IN_USE = "IN_USE";
    public static final String OVERDUE = "OVERDUE";
    public static final String COMPLETED = "COMPLETED";
    public static final String CANCELLED = "CANCELLED";

    private static final Map<String, String> LABELS = new LinkedHashMap<>();

    static {
        LABELS.put(PENDING, "待确认");
        LABELS.put(CONFIRMED, "已确认");
        LABELS.put(IN_USE, "使用中");
        LABELS.put(OVERDUE, "已逾期");
        LABELS.put(COMPLETED, "已完成");
        LABELS.put(CANCELLED, "已取消");
    }

    private OrderStatus() {
    }

    /**
     * 状态 → 中文标签。未知状态原样返回，null 返回空串（宁可展示原值，也不要在页面上显示 null）。
     */
    public static String labelOf(String status) {
        if (status == null) {
            return "";
        }
        return LABELS.getOrDefault(status, status);
    }
}
