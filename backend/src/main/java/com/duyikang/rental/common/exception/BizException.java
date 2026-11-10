package com.duyikang.rental.common.exception;

import com.duyikang.rental.common.constant.ErrorCode;

/**
 * 业务异常。
 *
 * <p>Service 层发现业务规则被违反时抛出，由 {@link GlobalExceptionHandler} 统一转成 {@code Result}。
 * Controller 内不要 try-catch 本异常（PROJECT_RULES.md §8.1）。</p>
 *
 * <p><b>可携带 data</b>：某些业务拒绝需要把「后续怎么办」一并告诉前端，典型是 K2 时段冲突——
 * 只回一句「已被占用」用户体验很差，必须同时给出冲突日期与候选时段，前端才能高亮日历
 * （PROJECT_RULES.md §8.3「错误码口径」）。{@code data} 会被原样放进 {@code Result.data}。</p>
 */
public class BizException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int code;

    /** 附加数据，可为 null；非 null 时原样作为 {@code Result.data} 返回给前端。 */
    private final Object data;

    public BizException(ErrorCode errorCode) {
        this(errorCode.getCode(), errorCode.getMessage(), null);
    }

    public BizException(ErrorCode errorCode, String message) {
        this(errorCode.getCode(), message, null);
    }

    public BizException(int code, String message) {
        this(code, message, null);
    }

    /** 带附加数据的完整构造。 */
    public BizException(ErrorCode errorCode, String message, Object data) {
        this(errorCode.getCode(), message, data);
    }

    public BizException(int code, String message, Object data) {
        super(message);
        this.code = code;
        this.data = data;
    }

    public int getCode() {
        return code;
    }

    public Object getData() {
        return data;
    }

    /** 便捷构造：时段冲突，不附带冲突明细。 */
    public static BizException occupancyConflict() {
        return new BizException(ErrorCode.OCCUPANCY_CONFLICT);
    }

    /**
     * 便捷构造：时段冲突，附带冲突日期与候选时段（前端用它高亮日历）。
     *
     * @param message 面向用户的提示，例如「2026-10-02 至 2026-10-04 已被租出」
     * @param data    冲突明细，建议为 DTO（含 conflictDates、suggestRanges），禁止塞 Entity
     */
    public static BizException occupancyConflict(String message, Object data) {
        return new BizException(ErrorCode.OCCUPANCY_CONFLICT, message, data);
    }

    /** 便捷构造：参数不合法。 */
    public static BizException paramError(String message) {
        return new BizException(ErrorCode.PARAM_ERROR, message);
    }

    /** 便捷构造：任意错误码 + 附加数据。 */
    public static BizException of(ErrorCode errorCode, Object data) {
        return new BizException(errorCode, errorCode.getMessage(), data);
    }
}
