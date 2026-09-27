package com.zhul.erp.common.exception;

import com.zhul.erp.common.result.ErrorData;
import com.zhul.erp.common.result.Result;
import com.zhul.erp.common.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public Result<ErrorData> handleBizException(BizException e) {
        log.warn("业务异常: code={}, errorCode={}, message={}", e.getCode(), e.getErrorCode(), e.getMessage());
        Result<ErrorData> result = new Result<>();
        result.setCode(e.getCode());
        result.setMessage(e.getMessage());
        if (e.getErrorCode() != null) {
            result.setData(new ErrorData(e.getErrorCode(), e.getDetail()));
        }
        return result;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", message);
        return Result.fail(message.isEmpty() ? ResultCode.PARAM_ERROR.getMessage() : message);
    }

    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleBindException(BindException e) {
        String message = e.getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("绑定参数失败: {}", message);
        Result<Void> result = new Result<>();
        result.setCode(ResultCode.PARAM_ERROR.getCode());
        result.setMessage(message.isEmpty() ? ResultCode.PARAM_ERROR.getMessage() : message);
        return result;
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<Void> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException e) {
        log.warn("上传文件过大: {}", e.getMessage());
        return Result.fail("上传文件过大，请压缩后重试");
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Void> handleAccessDeniedException(AccessDeniedException e) {
        log.warn("权限不足: {}", e.getMessage());
        return Result.fail(ResultCode.FORBIDDEN);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.fail(ResultCode.FAIL);
    }
}
