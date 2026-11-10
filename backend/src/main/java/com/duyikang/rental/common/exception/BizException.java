package com.duyikang.rental.common.exception;

import com.duyikang.rental.common.constant.ErrorCode;

/**
 * 业务异常。
 *
 * <p>Service 层发现业务规则被违反时抛出，由 {@link GlobalExceptionHandler} 统一转成 {@code Result}。
 * Controller 内不要 try-catch 本异常（PROJECT_RULES.md §8.1）。</p>
 */
public class BizException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int code;

    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    /** 便捷构造：时段冲突，供 K2 冲突检测使用。 */
    public static BizException occupancyConflict() {
        return new BizException(ErrorCode.OCCUPANCY_CONFLICT);
    }

    /** 便捷构造：参数不合法。 */
    public static BizException paramError(String message) {
        return new BizException(ErrorCode.PARAM_ERROR, message);
    }
}
