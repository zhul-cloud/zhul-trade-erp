package com.zhul.erp.modules.product.candidate.support;

import java.util.regex.Pattern;

/**
 * 询盘写的是不是型号：含至少一个数字，不含逗号、中文，且纯字母的单词（3 个字母以上）少于两个
 * （「Contactor, 32A」「Thermal overload relay 9-13A」是描述；「6ES7 214 1AG40 0XB0」是型号）。判错的有兜底：没进池的由采购填真实型号，误进池的可驳回为「不是型号」。
 */
public final class ModelRules {

    private static final Pattern DIGIT = Pattern.compile("\\d");
    private static final Pattern COMMA_OR_CJK = Pattern.compile("[,，、\\u4e00-\\u9fa5]");
    private static final Pattern WORD = Pattern.compile("[A-Za-z]{3,}");

    private ModelRules() {
    }

    public static boolean looksLikeModel(String text) {
        String s = text == null ? "" : text.trim();
        if (s.isEmpty() || !DIGIT.matcher(s).find() || COMMA_OR_CJK.matcher(s).find()) {
            return false;
        }
        int words = 0;
        for (String token : s.split("\\s+")) {
            if (WORD.matcher(token).matches()) {
                words++;
            }
        }
        return words < 2;
    }
}
