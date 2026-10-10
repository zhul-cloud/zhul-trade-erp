package com.zhul.erp.common.exception;

import com.zhul.erp.common.result.ErrorData;
import com.zhul.erp.common.result.Result;
import com.zhul.erp.common.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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

    /** 请求方法不对：按 405 返回，并告诉调用方该用什么方法 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        String supported = e.getSupportedHttpMethods() == null ? ""
                : e.getSupportedHttpMethods().stream().map(Object::toString).collect(Collectors.joining("、"));
        String message = "接口不支持 " + e.getMethod() + " 请求" + (supported.isEmpty() ? "" : "，请使用 " + supported);
        return clientError(HttpStatus.METHOD_NOT_ALLOWED, message, e);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> handleNoResource(NoResourceFoundException e) {
        return clientError(HttpStatus.NOT_FOUND, "接口不存在：/" + e.getResourcePath(), e);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException e) {
        return clientError(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "不支持的请求内容类型：" + e.getContentType(), e);
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<Result<Void>> handleMediaTypeNotAcceptable(HttpMediaTypeNotAcceptableException e) {
        return clientError(HttpStatus.NOT_ACCEPTABLE, "无法按请求的格式返回结果", e);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Result<Void>> handleMissingParameter(MissingServletRequestParameterException e) {
        return clientError(HttpStatus.BAD_REQUEST, "缺少参数：" + e.getParameterName(), e);
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<Result<Void>> handleMissingPart(MissingServletRequestPartException e) {
        return clientError(HttpStatus.BAD_REQUEST, "缺少上传内容：" + e.getRequestPartName(), e);
    }

    /** 其余请求绑定错误（缺少请求头、Cookie 等）；缺少参数是它的子类，由更具体的方法处理 */
    @ExceptionHandler(ServletRequestBindingException.class)
    public ResponseEntity<Result<Void>> handleRequestBinding(ServletRequestBindingException e) {
        return clientError(HttpStatus.BAD_REQUEST, ResultCode.PARAM_ERROR.getMessage(), e);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return clientError(HttpStatus.BAD_REQUEST, "参数格式不正确：" + e.getName(), e);
    }

    /** 请求体不是合法 JSON，或字段类型对不上（如数字字段传了文字） */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleNotReadable(HttpMessageNotReadableException e) {
        return clientError(HttpStatus.BAD_REQUEST, "请求内容格式不正确", e);
    }

    /** 调用方的请求错误：记 WARN 不记堆栈，HTTP 状态与 code 一致 */
    private static ResponseEntity<Result<Void>> clientError(HttpStatus status, String message, Exception e) {
        log.warn("请求错误: status={}, message={}, cause={}", status.value(), message, e.getMessage());
        Result<Void> result = new Result<>();
        result.setCode(status.value());
        result.setMessage(message);
        return ResponseEntity.status(status).body(result);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.fail(ResultCode.FAIL);
    }
}
