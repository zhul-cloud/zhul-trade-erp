package com.zhul.erp.modules.product.support;

import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.product.constants.ProductErrorCodes;

import java.util.Locale;

/**
 * 商品资料语言（spec product/product「商品规格参数」「商品应用场景」）：商品资料只存中文，英文、俄文属于营销内容。
 * 规格、应用场景、兼容说明的维护接口只接受 zh，不传为中文；以前写入的英文、俄文不再读写。
 */
public final class LangRules {

    public static final String DEFAULT = "zh";

    private LangRules() {
    }

    public static String of(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT;
        }
        String lang = raw.trim().toLowerCase(Locale.ROOT);
        if (!DEFAULT.equals(lang)) {
            throw BizException.of(ProductErrorCodes.PARAM_INVALID, "商品资料只维护中文（lang 只能是 zh）");
        }
        return lang;
    }
}
