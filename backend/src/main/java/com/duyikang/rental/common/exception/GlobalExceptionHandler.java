package com.duyikang.rental.common.exception;

import com.duyikang.rental.common.constant.ErrorCode;
import com.duyikang.rental.common.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

/**
 * 全局异常处理。
 *
 * <p>约定（PROJECT_RULES.md §8.1）：Controller 不写 try-catch，业务异常一律经此处转换。
 * 注意 {@link DuplicateKeyException} 的处理——K2 的唯一索引兜底靠它转 4090，
 * 若漏了这一步，冲突会冒成 500（AI 协作手册「版本陷阱 #9」）。</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 业务异常：可预期，warn 级别，不打全栈；若异常带 data（如 4090 的冲突明细）则一并返回。 */
    @ExceptionHandler(BizException.class)
    public Result<Object> handleBizException(BizException e, HttpServletRequest request) {
        log.warn("业务异常 uri={} code={} msg={}", request.getRequestURI(), e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage(), e.getData());
    }

    /** @RequestBody + @Valid 校验失败。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("；"));
        return Result.fail(ErrorCode.PARAM_ERROR, msg.isEmpty() ? ErrorCode.PARAM_ERROR.getMessage() : msg);
    }

    /** 表单/查询参数绑定校验失败。 */
    @ExceptionHandler(BindException.class)
    public Result<Void> handleBindException(BindException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("；"));
        return Result.fail(ErrorCode.PARAM_ERROR, msg.isEmpty() ? ErrorCode.PARAM_ERROR.getMessage() : msg);
    }

    /** 方法参数上的 @Validated 约束失败。 */
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraintViolation(ConstraintViolationException e) {
        String msg = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("；"));
        return Result.fail(ErrorCode.PARAM_ERROR, msg);
    }

    /** 缺参数。 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingParam(MissingServletRequestParameterException e) {
        return Result.fail(ErrorCode.PARAM_MISSING, "缺少参数：" + e.getParameterName());
    }

    /** 参数类型不匹配，如把 "abc" 传给了 Long。 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return Result.fail(ErrorCode.PARAM_FORMAT_ERROR, "参数格式错误：" + e.getName());
    }

    /** 请求体不是合法 JSON。 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleNotReadable(HttpMessageNotReadableException e) {
        return Result.fail(ErrorCode.PARAM_FORMAT_ERROR, "请求体格式错误");
    }

    /**
     * 唯一索引冲突。
     *
     * <p>K2 场景：并发下单时两条请求同时通过应用层预检与 Redis 锁，最后由
     * {@code uk_occupancy(item_id, occupy_date)} 兜底，此处转 4090。
     * 其它表（如用户名重复）按业务含义处理，默认仍返回 4090，
     * 调用方若需区分应在 Service 内先做存在性校验。</p>
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public Result<Void> handleDuplicateKey(DuplicateKeyException e) {
        log.warn("唯一索引冲突：{}", e.getMostSpecificCause().getMessage());
        return Result.fail(ErrorCode.OCCUPANCY_CONFLICT, "该时段已被占用，请选择其他日期");
    }

    /** 无权访问（Spring Security 抛出）。 */
    @ExceptionHandler(AccessDeniedException.class)
    public Result<Void> handleAccessDenied(AccessDeniedException e) {
        return Result.fail(ErrorCode.FORBIDDEN);
    }

    /** 兜底：未预期异常，error 级别 + 全栈，对外不暴露内部细节。 */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e, HttpServletRequest request) {
        log.error("未预期异常 uri={}", request.getRequestURI(), e);
        return Result.fail(ErrorCode.SYSTEM_ERROR);
    }

    private String formatFieldError(FieldError fieldError) {
        return fieldError.getField() + " " + fieldError.getDefaultMessage();
    }
}
