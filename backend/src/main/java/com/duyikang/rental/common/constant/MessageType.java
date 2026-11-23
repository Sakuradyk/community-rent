package com.duyikang.rental.common.constant;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 站内消息的类型字典。
 *
 * <p>两类常量分开是有意的：{@code type} 决定前端的图标/分组，{@code BIZ_*} 决定点击后跳哪一页。
 * 二者的取值集合不同（未来可能多个 type 共用同一个 biz 页面），混成一个枚举会导致"加一个跳转目标就要改类型"。</p>
 */
public final class MessageType {

    /** 订单相关（下单、确认、取用、取消、结算、裁决） */
    public static final String ORDER = "ORDER";
    /** 信誉变动 */
    public static final String CREDIT = "CREDIT";
    /** 物品检修 / 损耗 */
    public static final String MAINTAIN = "MAINTAIN";
    /** 系统通知 */
    public static final String SYSTEM = "SYSTEM";

    /** 点击后跳到订单详情（bizId = orderId） */
    public static final String BIZ_ORDER = "ORDER";
    /** 点击后跳到结算单（bizId = orderId） */
    public static final String BIZ_SETTLEMENT = "SETTLEMENT";
    /** 点击后跳到物品详情（bizId = itemId） */
    public static final String BIZ_ITEM = "ITEM";

    /** 未读 / 已读 */
    public static final int UNREAD = 0;
    public static final int READ = 1;

    private static final Map<String, String> TYPE_LABELS = new LinkedHashMap<>();

    static {
        TYPE_LABELS.put(ORDER, "订单");
        TYPE_LABELS.put(CREDIT, "信誉");
        TYPE_LABELS.put(MAINTAIN, "检修");
        TYPE_LABELS.put(SYSTEM, "系统");
    }

    private MessageType() {
    }

    /**
     * 类型的中文名（未知类型原样返回，不抛异常 —— 消息是旁路功能，不该因为脏数据阻断页面）。
     */
    public static String labelOf(String type) {
        if (type == null) {
            return "";
        }
        return TYPE_LABELS.getOrDefault(type, type);
    }
}
