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
import org.springframework.web.multipart.MaxUploadSizeExceededException;

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
     * 唯一索引冲突，<b>按约束名分流</b>。
     *
     * <p>为什么不一律返回 4090：4090 的文案是「该时段已被占用，请选择其他日期」，
     * 若用户注册时重复手机号也被回成这句，前端会错误地弹出日期选择器——
     * 错误码不仅要"对"，还要"能指导用户下一步动作"（PROJECT_RULES.md §7.2）。</p>
     *
     * <ul>
     *   <li>{@code uk_user_phone} → 2001「手机号已注册」；</li>
     *   <li>{@code uk_occupancy} 及未识别的约束 → 4090（K2 兜底仍走这条，
     *       避免新表的唯一键冲突冒成 500）。</li>
     * </ul>
     *
     * <p>未识别的约束会额外打一条 {@code unmapped duplicate key} 日志，
     * 便于后续按真实约束名补齐分流规则。</p>
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public Result<Void> handleDuplicateKey(DuplicateKeyException e) {
        String detail = e.getMostSpecificCause().getMessage();
        if (detail != null && detail.contains("uk_user_phone")) {
            log.warn("唯一索引冲突（手机号重复）：{}", detail);
            return Result.fail(ErrorCode.PARAM_ERROR, "手机号已注册");
        }
        if (detail != null && detail.contains("uk_occupancy")) {
            log.warn("唯一索引冲突（时段被占）：{}", detail);
            return Result.fail(ErrorCode.OCCUPANCY_CONFLICT, "该时段已被占用，请选择其他日期");
        }
        log.warn("unmapped duplicate key：{}", detail);
        return Result.fail(ErrorCode.OCCUPANCY_CONFLICT, "该时段已被占用，请选择其他日期");
    }

    /**
     * 上传文件超过 multipart 上限。
     *
     * <p>不处理会落到兜底 {@code Exception} → 5000，把「用户选了大图」报成「系统故障」，
     * 属于错误码段位错用。{@code spring.servlet.multipart.max-file-size} 与
     * {@code file.max-size-mb} 必须同步改（PROJECT_RULES.md §9）。</p>
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<Void> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        log.warn("上传文件超限：{}", e.getMessage());
        return Result.fail(ErrorCode.FILE_TOO_LARGE);
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
