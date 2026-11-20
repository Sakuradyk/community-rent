package com.duyikang.rental.common.constant;

import java.util.Map;

/**
 * 结算单状态（K3）。
 *
 * <p>归还的那一刻生成结算单但**不直接生效**：承租方有 48 小时冷静期，
 * 可以点「无异议」接受出租方认定的损耗，也可以「提异议」把争议交给管理员裁决。
 * 这就是「不做真实支付」前提下，承租方对损耗认定的对价手段（替代电商的「确认收货」）。</p>
 */
public final class SettlementStatus {

    /**
     * 待承租方确认（冷静期内）
     */
    public static final String PENDING_CONFIRM = "PENDING_CONFIRM";

    /**
     * 已结算（承租方确认、超时自动确认、或申诉被驳回）
     */
    public static final String SETTLED = "SETTLED";

    /**
     * 承租方有异议，申诉处理中
     */
    public static final String DISPUTED = "DISPUTED";

    /**
     * 已被管理员裁决调整
     */
    public static final String ADJUSTED = "ADJUSTED";

    private static final Map<String, String> LABELS = Map.of(
            PENDING_CONFIRM, "待确认",
            SETTLED, "已结算",
            DISPUTED, "有异议",
            ADJUSTED, "已调整"
    );

    private SettlementStatus() {
    }

    /**
     * 状态中文名，未知状态原样返回
     */
    public static String labelOf(String status) {
        if (status == null) {
            return "";
        }
        return LABELS.getOrDefault(status, status);
    }
}
