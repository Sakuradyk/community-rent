package com.duyikang.rental.common.constant;

import java.util.Map;

/**
 * 归还时物品的状态（K3 损耗计算的自变量之一）。
 *
 * <p>三个取值直接对应 `loss_policy` 的 `return_coef_intact / return_coef_worn / return_coef_damaged`
 * （默认 1.0 / 1.5 / 3.0）：同样是租 3 天，完好归还是基准损耗，明显磨损翻倍，损坏翻三倍。</p>
 */
public final class ReturnState {

    /**
     * 完好
     */
    public static final String INTACT = "INTACT";

    /**
     * 明显磨损
     */
    public static final String WORN = "WORN";

    /**
     * 损坏
     */
    public static final String DAMAGED = "DAMAGED";

    private static final Map<String, String> LABELS = Map.of(
            INTACT, "完好",
            WORN, "轻微磨损",
            DAMAGED, "损坏"
    );

    private ReturnState() {
    }

    /**
     * 是否为合法取值
     */
    public static boolean isValid(String state) {
        return LABELS.containsKey(state);
    }

    /**
     * 中文名，未知状态原样返回
     */
    public static String labelOf(String state) {
        if (state == null) {
            return "";
        }
        return LABELS.getOrDefault(state, state);
    }
}
