package com.zhul.erp.modules.website.support;

import java.util.Locale;
import java.util.StringJoiner;
import java.util.regex.Pattern;

/**
 * 链接别名，与站点仓库的 productSlug 完全一致（docs/00-总体架构/独立站站点数据约定.md）：
 * slugPiece = 转小写 → 空白、_、.、/ 连续出现换成一个 - → 去掉 a-z、0-9、- 以外的字符 → 合并连续的 - → 去掉首尾 -；
 * slug = 品牌、系列、型号三段去掉空的后用 - 连接。站点构建时会重算并校验，不一致则构建失败。
 */
public final class SiteSlug {

    private static final Pattern SEPARATORS = Pattern.compile("(?U)[\\s_./]+");
    private static final Pattern OTHERS = Pattern.compile("[^a-z0-9-]");
    private static final Pattern DASHES = Pattern.compile("-+");

    private SiteSlug() {
    }

    public static String piece(String s) {
        String v = s == null ? "" : s.toLowerCase(Locale.ROOT);
        v = SEPARATORS.matcher(v).replaceAll("-");
        v = OTHERS.matcher(v).replaceAll("");
        v = DASHES.matcher(v).replaceAll("-");
        if (v.startsWith("-")) {
            v = v.substring(1);
        }
        if (v.endsWith("-")) {
            v = v.substring(0, v.length() - 1);
        }
        return v;
    }

    public static String of(String brand, String series, String model) {
        StringJoiner j = new StringJoiner("-");
        for (String part : new String[] {piece(brand), piece(series), piece(model)}) {
            if (!part.isEmpty()) {
                j.add(part);
            }
        }
        return j.toString();
    }
}
