package com.zhul.erp.modules.product.support;

import java.util.regex.Pattern;

/**
 * 文件地址白名单：只允许 http://、https:// 或以单个 / 开头的站内路径（design.md 决策 12）。
 * 拒绝 javascript:、data:、//host（协议相对地址）、/\host（浏览器会当成 //host）以及含空白和控制字符的地址。
 */
public final class UrlRules {

    private static final Pattern SAFE_URL = Pattern.compile(
            "^(?:https?://[^\\s\\x00-\\x1f\\x7f]+|/(?![/\\\\])[^\\s\\x00-\\x1f\\x7f]*)$", Pattern.CASE_INSENSITIVE);

    private UrlRules() {
    }

    public static boolean isSafe(String url) {
        return url != null && SAFE_URL.matcher(url).matches();
    }
}
