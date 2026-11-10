package com.duyikang.rental.common.result;

import com.duyikang.rental.common.constant.ErrorCode;
import org.slf4j.MDC;

import java.io.Serializable;

/**
 * 统一响应体。
 *
 * <p>约定（PROJECT_RULES.md §7）：所有接口返回 {@code {code, message, data, traceId}}，
 * code 段位见 {@link ErrorCode}，禁止自造段位。</p>
 *
 * @param <T> 业务数据类型
 */
public class Result<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 业务状态码，0 表示成功。 */
    private int code;

    /** 提示信息，成功时为 "成功"。 */
    private String message;

    /** 业务数据，失败时通常为 null。 */
    private T data;

    /** 链路追踪号，来自 MDC；便于按一次请求检索日志。 */
    private String traceId;

    public Result() {
    }

    public Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.traceId = MDC.get("traceId");
    }

    public static <T> Result<T> ok(T data) {
        return new Result<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessage(), data);
    }

    public static Result<Void> ok() {
        return new Result<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessage(), null);
    }

    public static <T> Result<T> fail(ErrorCode errorCode) {
        return new Result<>(errorCode.getCode(), errorCode.getMessage(), null);
    }

    public static <T> Result<T> fail(ErrorCode errorCode, String message) {
        return new Result<>(errorCode.getCode(), message, null);
    }

    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }

    /**
     * 失败但带数据：仅用于「拒绝的同时还要告诉前端怎么办」。
     *
     * <p>目前唯一场景是 4090 时段冲突——必须带 conflictDates 与 suggestRanges，
     * 否则前端只能弹一句「已被占用」（PROJECT_RULES.md §8.3）。</p>
     */
    public static <T> Result<T> fail(ErrorCode errorCode, String message, T data) {
        return new Result<>(errorCode.getCode(), message, data);
    }

    public static <T> Result<T> fail(int code, String message, T data) {
        return new Result<>(code, message, data);
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    /** 是否成功，供 Service 内部或测试断言使用。 */
    public boolean isSuccess() {
        return this.code == ErrorCode.SUCCESS.getCode();
    }
}
