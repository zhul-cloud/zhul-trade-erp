package com.zhul.erp.modules.product.support;

import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.product.constants.ProductErrorCodes;

/** 文本入参的通用校验：统一去首尾空格，再校验必填与长度。 */
public final class TextRules {

    private TextRules() {
    }

    /** 必填：去空格后不能为空，且不超过 max 个字符 */
    public static String required(String value, String label, int max) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) {
            throw BizException.of(ProductErrorCodes.PARAM_INVALID, label + "不能为空");
        }
        return checkLength(text, label, max);
    }

    /** 选填：null 视为空串，去空格后不超过 max 个字符 */
    public static String optional(String value, String label, int max) {
        return checkLength(value == null ? "" : value.trim(), label, max);
    }

    private static String checkLength(String text, String label, int max) {
        if (text.length() > max) {
            throw BizException.of(ProductErrorCodes.PARAM_INVALID, label + "长度不能超过" + max + "个字符");
        }
        return text;
    }

    /** 状态值只能是 0 或 1 */
    public static int status(Integer value) {
        if (value == null || (value != 0 && value != 1)) {
            throw BizException.of(ProductErrorCodes.PARAM_INVALID, "状态只能是 0（禁用）或 1（启用）");
        }
        return value;
    }
}
