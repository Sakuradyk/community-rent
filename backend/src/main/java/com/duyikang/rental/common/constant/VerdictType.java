package com.duyikang.rental.common.constant;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 申诉裁定类型（K3）。
 *
 * <p>为什么是四种而不是一个布尔：申诉人是<b>双方</b>，而"支持"这个词对双方的方向是相反的 ——
 * 承租方胜诉 = 损耗认定不成立（损耗费归零）；出租方胜诉 = 损耗认定成立（损耗费恢复）。
 * 用 `accepted=true/false` 表达时，出租方胜诉会被系统当成"支持承租方"把损耗费清零，
 * 于是出租方赢了官司反而自己承担损失。四种取值把"谁的主张成立"钉死在数据里。</p>
 *
 * <p>其中 `PARTIAL`（部分支持）是真实客服裁决里最常见的一档：认定损耗存在但金额过高，
 * 由管理员直接给出应扣金额，取值范围 0 ~ 原损耗费。</p>
 */
public final class VerdictType {

    /** 支持承租方：损耗不成立，损耗费归零 */
    public static final String RENTER_WIN = "RENTER_WIN";

    /** 支持出租方：损耗成立，损耗费按结算单里的 loss_ratio 恢复 */
    public static final String OWNER_WIN = "OWNER_WIN";

    /** 部分支持：认定损耗存在但金额由管理员裁定（必须给 lossFeeOverride） */
    public static final String PARTIAL = "PARTIAL";

    /** 驳回：维持原判，金额不动 */
    public static final String REJECTED = "REJECTED";

    private static final Map<String, String> LABELS = new LinkedHashMap<>();

    static {
        LABELS.put(RENTER_WIN, "支持承租方（损耗不成立）");
        LABELS.put(OWNER_WIN, "支持出租方（损耗成立）");
        LABELS.put(PARTIAL, "部分支持（裁定损耗金额）");
        LABELS.put(REJECTED, "驳回（维持原判）");
    }

    private VerdictType() {
    }

    /**
     * 取值是否合法
     *
     * @param type 裁定类型
     * @return 四种合法取值之一则返回 true
     */
    public static boolean isValid(String type) {
        return type != null && LABELS.containsKey(type);
    }

    /**
     * 中文标签（未知取值原样返回）
     *
     * @param type 裁定类型
     * @return 中文标签
     */
    public static String labelOf(String type) {
        if (type == null || type.isBlank()) {
            return "";
        }
        return LABELS.getOrDefault(type, type);
    }
}
