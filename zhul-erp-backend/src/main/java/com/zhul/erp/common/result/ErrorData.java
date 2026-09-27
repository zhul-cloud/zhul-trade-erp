package com.zhul.erp.common.result;

/**
 * 业务错误响应里 {@code data} 的内容：字符串错误码和可选的补充信息。
 * 数字 {@code code} 与 {@code message} 保持原有约定，前端按 {@code data.errorCode} 区分具体错误。
 */
public record ErrorData(String errorCode, Object detail) {
}
