package com.zhul.erp.modules.website.support;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 站点数据里一个商品的取值规则（spec website-import「站点原有内容与 ERP 内容的取舍」）。
 * 字段按 ERP 里的来源分组；没有导入过的商品全部用 ERP 的值。导入过的商品，每组按以下顺序取：
 * 1. 运营对这一组点过「改用 ERP 内容」→ ERP；
 * 2. 这一组在 ERP 里的来源在导入时间之后更新过 → ERP；
 * 3. 否则用站点原有内容（原样）。
 * 身份字段（品牌、型号、系列、站点品类、链接别名、发布单号）始终用 ERP；不属于任何组的站点独有字段始终用原有内容。
 * ERP 的值为空时不写这个字段（站点按没有处理）。
 */
public final class SiteEntry {

    /** 字段组 → 站点字段 */
    public static final Map<String, List<String>> GROUPS = groups();
    /** 始终取 ERP 的字段 */
    public static final List<String> IDENTITY = List.of("brand", "model", "series", "cat", "slug", "release");

    private SiteEntry() {
    }

    private static Map<String, List<String>> groups() {
        Map<String, List<String>> g = new LinkedHashMap<>();
        g.put("spec", List.of("spec"));
        g.put("content_en", List.of("overview", "meta_description"));
        g.put("content_ru", List.of("overview_ru", "meta_description_ru"));
        g.put("spec_ru", List.of("spec_ru"));
        g.put("specs", List.of("specs"));
        g.put("applications", List.of("applications"));
        g.put("faq", List.of("faq"));
        g.put("compatibility", List.of("compatibility"));
        g.put("deep_ru", List.of("deep_ru"));
        // linkedin 图 ERP 没有对应位置，始终用站点原有内容
        g.put("media", List.of("photo", "photos", "videos"));
        g.put("price", List.of("sell_price", "sell_price_currency", "price_source"));
        g.put("status", List.of("status"));
        return Collections.unmodifiableMap(g);
    }

    /**
     * @param identity   身份字段的 ERP 值
     * @param erp        ERP 的值：站点字段 → 值（只放有值的）
     * @param erpUpdated 字段组 → 这一组在 ERP 里来源的最近更新时间（没有来源为 null）
     * @param original   站点原有内容；没有导入过为 null
     * @param importedAt 导入时间；没有导入过为 null
     * @param erpFields  点过「改用 ERP 内容」的字段组
     */
    public static Map<String, Object> build(Map<String, Object> identity, Map<String, Object> erp, Map<String, LocalDateTime> erpUpdated,
                                            Map<String, Object> original, LocalDateTime importedAt, Collection<String> erpFields) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (original != null) {
            // 先按原有内容的字段顺序放好，再覆盖，比较差异时顺序稳定
            out.putAll(original);
        }
        for (Map.Entry<String, List<String>> g : GROUPS.entrySet()) {
            if (!useErp(g.getKey(), erpUpdated, original, importedAt, erpFields)) {
                continue;
            }
            for (String key : g.getValue()) {
                Object v = erp.get(key);
                if (empty(v)) {
                    out.remove(key);
                } else {
                    out.put(key, v);
                }
            }
        }
        for (String key : IDENTITY) {
            Object v = identity.get(key);
            if (empty(v) && !"series".equals(key)) {
                out.remove(key);
            } else {
                out.put(key, v == null ? "" : v);
            }
        }
        return out;
    }

    /** 这一组是否取 ERP 的值 */
    public static boolean useErp(String group, Map<String, LocalDateTime> erpUpdated, Map<String, Object> original,
                                 LocalDateTime importedAt, Collection<String> erpFields) {
        if (original == null || importedAt == null) {
            return true;
        }
        if (erpFields != null && erpFields.contains(group)) {
            return true;
        }
        LocalDateTime updated = erpUpdated == null ? null : erpUpdated.get(group);
        return updated != null && updated.isAfter(importedAt);
    }

    /** 站点字段所属的组；身份字段与站点独有字段为 null */
    public static String groupOf(String key) {
        for (Map.Entry<String, List<String>> g : GROUPS.entrySet()) {
            if (g.getValue().contains(key)) {
                return g.getKey();
            }
        }
        return null;
    }

    public static Set<String> groupNames() {
        return GROUPS.keySet();
    }

    private static boolean empty(Object v) {
        return v == null || (v instanceof String s && s.isBlank()) || (v instanceof Collection<?> c && c.isEmpty())
                || (v instanceof Map<?, ?> m && m.isEmpty());
    }
}
