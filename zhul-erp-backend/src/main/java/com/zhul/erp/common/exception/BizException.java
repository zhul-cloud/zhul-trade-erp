package com.zhul.erp.common.exception;

import com.zhul.erp.common.result.ResultCode;
import lombok.Getter;

@Getter
public class BizException extends RuntimeException {
    private final Integer code;
    /** 字符串错误码，如 PRODUCT_DUPLICATE；老代码抛出的异常没有 */
    private final String errorCode;
    /** 给前端的补充信息，如已存在商品的 ID */
    private final Object detail;

    public BizException(String message) {
        super(message);
        this.code = 500;
        this.errorCode = null;
        this.detail = null;
    }

    public BizException(ResultCode rc) {
        super(rc.getMessage());
        this.code = rc.getCode();
        this.errorCode = null;
        this.detail = null;
    }

    public BizException(ResultCode rc, String message) {
        super(message);
        this.code = rc.getCode();
        this.errorCode = null;
        this.detail = null;
    }

    private BizException(String errorCode, String message, Object detail) {
        super(message);
        this.code = 500;
        this.errorCode = errorCode;
        this.detail = detail;
    }

    public static BizException of(String errorCode, String message) {
        return new BizException(errorCode, message, null);
    }

    public static BizException of(String errorCode, String message, Object detail) {
        return new BizException(errorCode, message, detail);
    }
}
