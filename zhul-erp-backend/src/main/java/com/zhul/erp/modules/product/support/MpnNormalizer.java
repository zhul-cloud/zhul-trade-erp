package com.zhul.erp.modules.product.support;

import java.text.Normalizer;
import java.util.Locale;

/**
 * 型号归一化（design.md 决策 4）：去首尾空格 → NFKC → 小写 → 去掉所有非字母数字。
 * 结果只用于去重与检索，不用于展示。归一化为空表示型号无效。
 */
public final class MpnNormalizer {

    private MpnNormalizer() {
    }

    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String nfkc = Normalizer.normalize(raw.trim(), Normalizer.Form.NFKC);
        return nfkc.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
