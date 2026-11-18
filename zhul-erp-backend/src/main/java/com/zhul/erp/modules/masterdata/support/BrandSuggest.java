package com.zhul.erp.modules.masterdata.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 待确认品牌的建议（openspec brand-unify）：只是提示，由平台账号决定。
 * <ul>
 *   <li>不是品牌名：「未知品牌」「N/A」之类；</li>
 *   <li>含多个品牌：用 / , 、 等隔开的几段都能认出品牌；</li>
 *   <li>相近：去掉符号后与某个品牌名或别名相同、互相包含（≥4 个字符），或拼写只差 1～2 个字母。</li>
 * </ul>
 */
public final class BrandSuggest {

    public static final String KIND_SIMILAR = "similar";
    public static final String KIND_NOT_BRAND = "notBrand";

    private static final Set<String> NOT_BRAND = Set.of("未知品牌", "未知", "不详", "无", "无品牌", "其他", "其它", "unknown",
            "na", "n/a", "none", "other", "others", "nobrand", "no brand", "oem", "generic brand", "-", "/", "?");

    private BrandSuggest() {
    }

    /** 一个建议：类型、说明文字、建议的品牌（相近时） */
    public record Suggestion(String kind, String text, Long brandId, String brandName) {
    }

    /** 品牌库的一个写法：正式名或别名 */
    public record Entry(Long brandId, String brandName, String text, boolean alias) {
    }

    /** byKey：比较键（去首尾空格、转小写）→ 写法；entries：全部正式名与别名 */
    public static Suggestion of(String text, Map<String, Entry> byKey, List<Entry> entries) {
        String key = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        if (NOT_BRAND.contains(key)) {
            return new Suggestion(KIND_NOT_BRAND, "不是品牌名，建议标为不是品牌", null, null);
        }
        String[] parts = key.split("\\s*[/,，、;；|]\\s*");
        if (parts.length >= 2) {
            List<String> hits = new ArrayList<>();
            for (String p : parts) {
                Entry e = byKey.get(p.trim());
                if (e != null && !hits.contains(e.brandName())) {
                    hits.add(e.brandName());
                }
            }
            if (hits.size() >= 2) {
                return new Suggestion(KIND_NOT_BRAND, "含多个品牌，建议标为不是品牌", null, null);
            }
        }
        String alnum = alnum(key);
        if (alnum.isEmpty()) {
            return null;
        }
        Entry best = null;
        String why = null;
        for (Entry e : entries) {
            String ek = alnum(e.text().toLowerCase(Locale.ROOT));
            if (ek.isEmpty()) {
                continue;
            }
            if (ek.equals(alnum)) {
                best = e;
                why = "写法接近";
                break;
            }
            // 只差一两个字母时说「拼写接近」更贴切（Beckhof → Beckhoff），优先于「名称包含」
            if (!"拼写接近".equals(why) && alnum.length() >= 5 && ek.length() >= 5 && distance(alnum, ek) <= 2) {
                best = e;
                why = "拼写接近";
            }
            if (best == null && Math.min(ek.length(), alnum.length()) >= 4 && (alnum.contains(ek) || ek.contains(alnum))) {
                best = e;
                why = "名称包含";
            }
        }
        if (best == null) {
            return null;
        }
        String hint = best.alias() ? "（已有别名 " + best.text() + "）" : "（" + why + "）";
        return new Suggestion(KIND_SIMILAR, "相近：" + best.brandName() + hint, best.brandId(), best.brandName());
    }

    /** 只留字母、数字和汉字 */
    static String alnum(String s) {
        return s.replaceAll("[^\\p{L}\\p{N}]", "");
    }

    /** 编辑距离（只用于短的品牌名） */
    static int distance(String a, String b) {
        if (Math.abs(a.length() - b.length()) > 2) {
            return 3;
        }
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] t = prev;
            prev = cur;
            cur = t;
        }
        return prev[b.length()];
    }
}
