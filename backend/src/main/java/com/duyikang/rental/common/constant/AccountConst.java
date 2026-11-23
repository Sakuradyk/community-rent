package com.duyikang.rental.common.constant;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 账务常量：账户主体、余额桶、记账业务类型与动作。
 *
 * <p>为什么用常量类而不是枚举：这些值要与数据库里的 {@code CHECK} 约束、接口契约里的
 * 字符串保持逐字一致，写成常量便于一次性核对；真正的业务分支很少（都是"拿钱/给钱"）。</p>
 *
 * @author duyikang
 */
public final class AccountConst {

    private AccountConst() {
    }

    /** 账户主体：用户账户（每个用户一行，懒创建）。 */
    public static final String OWNER_USER = "USER";

    /** 账户主体：系统外部（全站唯一一行，代表用户自己的银行卡/其它账户在账上的镜像）。 */
    public static final String OWNER_EXTERNAL = "EXTERNAL";

    /** 外部账户固定 owner_id。 */
    public static final long EXTERNAL_OWNER_ID = 0L;

    /** 余额桶：可用余额。 */
    public static final String BUCKET_CASH = "CASH";

    /** 余额桶：冻结余额（在租订单的租金 + 押金）。 */
    public static final String BUCKET_FROZEN = "FROZEN";

    /** 业务类型：模拟充值。 */
    public static final String BIZ_RECHARGE = "RECHARGE";

    /** 业务类型：下单冻结。 */
    public static final String BIZ_ORDER_FREEZE = "ORDER_FREEZE";

    /** 业务类型：取消/未取用解冻。 */
    public static final String BIZ_ORDER_RELEASE = "ORDER_RELEASE";

    /** 业务类型：结算划转。 */
    public static final String BIZ_SETTLE = "SETTLE";

    /** 业务类型：裁决冲正。 */
    public static final String BIZ_VERDICT = "VERDICT";

    /** 业务类型：管理端手工调整。 */
    public static final String BIZ_ADMIN_ADJUST = "ADMIN_ADJUST";

    /** 动作：冻结。 */
    public static final String ACT_FREEZE = "FREEZE";

    /** 动作：解冻。 */
    public static final String ACT_RELEASE = "RELEASE";

    /** 动作：租金。 */
    public static final String ACT_RENT = "RENT";

    /** 动作：逾期费。 */
    public static final String ACT_OVERDUE = "OVERDUE";

    /** 动作：损耗费。 */
    public static final String ACT_LOSS = "LOSS";

    /** 动作：押金退回。 */
    public static final String ACT_REFUND = "REFUND";

    /** 动作：裁决冲正。 */
    public static final String ACT_ADJUST = "ADJUST";

    /** 动作：充值。 */
    public static final String ACT_RECHARGE = "RECHARGE";

    /** 资金流向说明，仅用于流水展示。 */
    private static final Map<String, String> ACTION_LABELS = new LinkedHashMap<>();

    static {
        ACTION_LABELS.put(ACT_FREEZE, "冻结押金与租金");
        ACTION_LABELS.put(ACT_RELEASE, "解除冻结");
        ACTION_LABELS.put(ACT_RENT, "租金");
        ACTION_LABELS.put(ACT_OVERDUE, "逾期费");
        ACTION_LABELS.put(ACT_LOSS, "损耗费");
        ACTION_LABELS.put(ACT_REFUND, "押金退回");
        ACTION_LABELS.put(ACT_ADJUST, "裁决冲正");
        ACTION_LABELS.put(ACT_RECHARGE, "充值");
    }

    /**
     * 动作的中文标签。
     *
     * @param action 动作码
     * @return 中文标签，未知返回原值
     */
    public static String labelOf(String action) {
        return ACTION_LABELS.getOrDefault(action, action);
    }
}
