package com.zhul.erp.modules.website.support;

import org.springframework.util.StringUtils;

import java.util.List;

/** 站点状态（spec website-publish「站点商品」）：默认按商品生命周期，停产为 discont，其他为 instock；可以手工改成 legacy */
public final class SiteStatus {

    public static final String INSTOCK = "instock";
    public static final String DISCONT = "discont";
    public static final String LEGACY = "legacy";
    public static final List<String> ALL = List.of(INSTOCK, DISCONT, LEGACY);

    /** 生命周期：4-已停产、5-停产无替代 */
    private static final List<Integer> DISCONTINUED = List.of(4, 5);

    private SiteStatus() {
    }

    public static String of(String manual, Integer lifecycleStatus) {
        if (StringUtils.hasText(manual)) {
            return manual;
        }
        return lifecycleStatus != null && DISCONTINUED.contains(lifecycleStatus) ? DISCONT : INSTOCK;
    }
}
