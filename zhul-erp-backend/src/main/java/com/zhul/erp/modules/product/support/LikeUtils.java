package com.zhul.erp.modules.product.support;

/** 模糊查询关键词转义：关键词里的 % _ \ 按字面匹配，不当作通配符。 */
public final class LikeUtils {

    private LikeUtils() {
    }

    public static String escape(String keyword) {
        return keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
