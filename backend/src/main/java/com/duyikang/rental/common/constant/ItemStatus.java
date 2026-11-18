package com.duyikang.rental.common.constant;

import java.util.Map;

/**
 * 物品状态机常量与中文标签。
 *
 * <p>为什么集中放这里：状态是前后端共同语言，前端 {@code web-user/src/utils/constants.js} 的
 * {@code ITEM_STATUS} 与本类必须逐字一致（PROJECT_RULES.md §4.6 F2：枚举单一来源）。
 * 后端只在一处定义，禁止在 Service 里散写字符串字面量。</p>
 */
public final class ItemStatus {

    /** 待审核：发布后的初始状态，不对外可见。 */
    public static final String PENDING = "PENDING";

    /** 在架：审核通过且未下架，列表页只返回这个状态。 */
    public static final String ON_SHELF = "ON_SHELF";

    /** 已下架：归属人主动下架，详情可看、列表不可见。 */
    public static final String OFF_SHELF = "OFF_SHELF";

    /** 租出中：有进行中订单，由订单状态机驱动。 */
    public static final String RENTED = "RENTED";

    /** 检修中：损耗值达阈值后的状态。 */
    public static final String MAINTAINING = "MAINTAINING";

    /** 审核驳回：归属人改后可重提（编辑即回到待审）。 */
    public static final String REJECTED = "REJECTED";

    private static final Map<String, String> LABELS = Map.of(
            PENDING, "待审核",
            ON_SHELF, "在架",
            OFF_SHELF, "已下架",
            RENTED, "租出中",
            MAINTAINING, "检修中",
            REJECTED, "审核驳回"
    );

    private ItemStatus() {
    }

    /** 取中文标签；未知状态原样返回，避免前端显示空白。 */
    public static String labelOf(String status) {
        if (status == null) {
            return "";
        }
        return LABELS.getOrDefault(status, status);
    }
}
