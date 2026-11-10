package com.duyikang.rental.common.constant;

/**
 * 业务错误码。
 *
 * <p>段位约定（PROJECT_RULES.md §7）：<br>
 * 0 成功 / 1xxx 认证与权限 / 2xxx 参数 / 3xxx 状态机与业务规则 /
 * 4xxx 费用与结算 / 4090 时段冲突（独立留号，供并发压测断言）/ 5xxx 系统。<br>
 * 禁止自定义新段位。</p>
 */
public enum ErrorCode {

    // ========== 成功 ==========
    SUCCESS(0, "成功"),

    // ========== 1xxx 认证与权限 ==========
    UNAUTHORIZED(1001, "未登录或登录已过期"),
    FORBIDDEN(1002, "无权限访问"),
    TOKEN_INVALID(1003, "登录凭证无效"),
    TOKEN_EXPIRED(1004, "登录凭证已过期"),
    ACCOUNT_OR_PASSWORD_ERROR(1005, "账号或密码错误"),
    ACCOUNT_DISABLED(1006, "账号已被禁用"),

    // ========== 2xxx 参数 ==========
    PARAM_ERROR(2001, "参数不合法"),
    PARAM_MISSING(2002, "缺少必要参数"),
    PARAM_FORMAT_ERROR(2003, "参数格式错误"),
    FILE_TYPE_NOT_ALLOWED(2004, "文件类型不支持"),
    FILE_TOO_LARGE(2005, "文件超出大小限制"),

    // ========== 3xxx 状态机与业务规则 ==========
    ORDER_STATE_INVALID(3001, "订单当前状态不允许该操作"),
    CREDIT_NOT_ENOUGH(3002, "信誉分不足，暂不能下单"),
    ITEM_STATUS_INVALID(3003, "物品当前状态不可租用"),
    ITEM_NOT_FOUND(3004, "物品不存在"),
    ORDER_NOT_FOUND(3005, "订单不存在"),
    SELF_RENT_FORBIDDEN(3006, "不能租用自己发布的物品"),
    ITEM_AUDIT_PENDING(3007, "物品正在审核中"),
    PERMISSION_DENIED_FOR_RESOURCE(3008, "无权操作该资源"),
    APPEAL_STATE_INVALID(3009, "申诉当前状态不允许该操作"),

    // ========== 4xxx 费用与结算 ==========
    FEE_OUT_OF_RANGE(4001, "金额超出允许范围"),
    DEPOSIT_INVALID(4002, "押金金额不合法"),
    SETTLEMENT_ALREADY_DONE(4003, "该订单已完成结算"),
    LOSS_RATIO_INVALID(4004, "损耗系数不合法"),
    /**
     * 时段冲突独立编号：唯一索引冲突 catch 后统一转此码，压测断言靠它计数。
     *
     * <p>本码是对「4xxx = 费用」段位规则的<b>唯一显式豁免</b>（PROJECT_RULES.md §7.2）。
     * 抛出时必须用 {@code BizException.occupancyConflict(msg, data)} 带上冲突日期与候选时段，
     * 否则前端只能干巴巴提示「已被占用」。</p>
     */
    OCCUPANCY_CONFLICT(4090, "该时段已被占用，请选择其他日期"),

    // ========== 5xxx 系统 ==========
    SYSTEM_ERROR(5000, "系统繁忙，请稍后重试"),
    DB_ERROR(5001, "数据访问异常"),
    CACHE_ERROR(5002, "缓存服务异常"),
    /**
     * 抢锁失败：<b>仅供内部诊断</b>（日志、定时任务、补偿任务），
     * <b>禁止</b>作为下单等用户接口的对外返回码 —— 用户视角里不存在「锁」，
     * 抢不到锁应伪装成 {@link #OCCUPANCY_CONFLICT}（PROJECT_RULES.md §8.3）。
     */
    LOCK_ACQUIRE_FAILED(5003, "系统繁忙，请重试"),
    REMOTE_CALL_ERROR(5004, "外部服务调用失败");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
