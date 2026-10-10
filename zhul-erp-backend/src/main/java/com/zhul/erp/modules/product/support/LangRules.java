package com.zhul.erp.modules.product.support;

import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.product.constants.ProductErrorCodes;

import java.util.Locale;
import java.util.Set;

/** 商品内容语言：zh-中文（对内）、en-英文、ru-俄文（对外）；不传为英文，兼容现有调用方 */
public final class LangRules {

    public static final String DEFAULT = "en";
    private static final Set<String> LANGS = Set.of("zh", "en", "ru");

    private LangRules() {
    }

    public static String of(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT;
        }
        String lang = raw.trim().toLowerCase(Locale.ROOT);
        if (!LANGS.contains(lang)) {
            throw BizException.of(ProductErrorCodes.PARAM_INVALID, "语言只能是 zh、en、ru");
        }
        return lang;
    }
}
