package com.zhul.erp.modules.product.content.support;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 资料包「物流信息」「海关信息」的字段识别与取值（spec product/product-content「上传 wiki 商品文件作为资料包」）。
 * 字段名兼容资料包写法与 llm-wiki 写法；值从自由文字里取：重量统一 kg（3 位小数）、尺寸统一 mm（1 位小数），
 * HS 编码去点后 6 到 10 位数字，原产地按中英文国家名换成两位国家码。标了「估」的重量、无法识别的值不取，给出说明。
 */
public final class FactFields {

    private FactFields() {
    }

    public static final String NET_WEIGHT = "netWeightKg";
    public static final String GROSS_WEIGHT = "grossWeightKg";
    public static final String LENGTH = "lengthMm";
    public static final String WIDTH = "widthMm";
    public static final String HEIGHT = "heightMm";
    public static final String PACKAGE_TYPE = "packageType";
    public static final String PACKAGE_QUANTITY = "packageQuantity";
    public static final String SHIPPING_NOTE = "shippingNote";
    public static final String HS_CODE = "hsCode";
    public static final String ORIGIN = "originCountry";
    public static final String NAME_CN = "customsNameCn";
    public static final String NAME_EN = "customsNameEn";
    /** 尺寸一行拆成长、宽、高三个字段 */
    private static final String SIZE = "size";

    /** 字段键 → 中文名（预览、提示用） */
    public static final Map<String, String> LABELS = labels();

    private static Map<String, String> labels() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(NET_WEIGHT, "净重（kg）");
        m.put(GROSS_WEIGHT, "毛重（kg）");
        m.put(LENGTH, "长（mm）");
        m.put(WIDTH, "宽（mm）");
        m.put(HEIGHT, "高（mm）");
        m.put(PACKAGE_TYPE, "包装方式");
        m.put(PACKAGE_QUANTITY, "单箱数量");
        m.put(SHIPPING_NOTE, "特殊运输要求");
        m.put(HS_CODE, "HS 编码");
        m.put(ORIGIN, "原产国");
        m.put(NAME_CN, "报关品名（中文）");
        m.put(NAME_EN, "报关品名（英文）");
        return m;
    }

    private static final Pattern NUMBER_UNIT = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(kg|g|千克|公斤|克)?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SIZE_PATTERN = Pattern.compile(
            "(\\d+(?:\\.\\d+)?)\\s*[×xX*]\\s*(\\d+(?:\\.\\d+)?)\\s*[×xX*]\\s*(\\d+(?:\\.\\d+)?)\\s*(mm|cm|毫米|厘米)?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern INTEGER = Pattern.compile("(\\d+)");
    private static final Map<String, String> COUNTRIES = countries();

    /** 字段名所属章节：LOGISTICS、CUSTOMS，或空（不认识） */
    public static ContentSection sectionOf(String label) {
        String k = key(label);
        if (k == null) {
            return null;
        }
        return switch (k) {
            case HS_CODE, ORIGIN, NAME_CN, NAME_EN -> ContentSection.CUSTOMS;
            default -> ContentSection.LOGISTICS;
        };
    }

    /** 字段名 → 字段键；「HS 编码（通用）」等不取的字段为空 */
    static String key(String label) {
        String n = label == null ? "" : label.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        if (n.startsWith("hs编码（通用）") || n.startsWith("hs编码(通用)")) {
            return null;
        }
        if (n.contains("净重") || n.startsWith("netweight")) {
            return NET_WEIGHT;
        }
        if (n.contains("毛重") || n.startsWith("grossweight")) {
            return GROSS_WEIGHT;
        }
        if (n.startsWith("尺寸") || n.startsWith("外形尺寸") || n.startsWith("dimension")) {
            return SIZE;
        }
        if (n.startsWith("包装方式") || n.startsWith("包装类型") || n.startsWith("packagetype")) {
            return PACKAGE_TYPE;
        }
        if (n.startsWith("单箱数量") || n.startsWith("包装数量")) {
            return PACKAGE_QUANTITY;
        }
        if (n.startsWith("特殊运输要求") || n.startsWith("运输备注")) {
            return SHIPPING_NOTE;
        }
        if (n.startsWith("hs编码") || n.startsWith("hscode")) {
            return HS_CODE;
        }
        if (n.startsWith("原产地") || n.startsWith("原产国") || n.startsWith("origin")) {
            return ORIGIN;
        }
        if ((n.contains("报关品名") || n.contains("申报品名")) && (n.contains("中文") || n.contains("cn"))) {
            return NAME_CN;
        }
        if ((n.contains("报关品名") || n.contains("申报品名")) && (n.contains("英文") || n.contains("en"))) {
            return NAME_EN;
        }
        return null;
    }

    /** 取一行的值写进块；不认识的字段、估计值、无法识别的值写进块提示 */
    static void put(ContentDraft.Block block, String label, String raw) {
        String k = key(label);
        if (k == null || FactFields.sectionOf(label) != block.getSection()) {
            block.getNotes().add("「" + label + "」不是" + block.getSection().label() + "的字段，已跳过");
            return;
        }
        String v = raw == null ? "" : raw.trim();
        if (v.isEmpty() || "—".equals(v) || "-".equals(v) || v.contains("待确认")) {
            return;
        }
        switch (k) {
            case NET_WEIGHT, GROSS_WEIGHT -> weight(block, k, label, v);
            case SIZE -> size(block, label, v);
            case PACKAGE_QUANTITY -> {
                Matcher m = INTEGER.matcher(v);
                if (m.find()) {
                    block.getFields().put(k, m.group(1));
                } else {
                    unknown(block, label, v);
                }
            }
            case PACKAGE_TYPE -> block.getFields().put(k, cut(head(v), 32));
            case SHIPPING_NOTE -> {
                if (!"无".equals(v) && !"none".equalsIgnoreCase(v)) {
                    block.getFields().put(k, cut(v, 255));
                }
            }
            case HS_CODE -> {
                String digits = head(v).replaceAll("[.\\s]", "");
                if (digits.matches("\\d{6,10}")) {
                    block.getFields().put(k, digits);
                } else {
                    block.getNotes().add("「" + label + "」的「" + v + "」不是 6 到 10 位数字的 HS 编码，未写入");
                }
            }
            case ORIGIN -> {
                String code = country(v);
                if (code != null) {
                    block.getFields().put(k, code);
                } else {
                    block.getNotes().add("「" + label + "」的「" + v + "」认不出国家，未写入");
                }
            }
            case NAME_CN, NAME_EN -> block.getFields().put(k, cut(head(v), 128));
            default -> unknown(block, label, v);
        }
    }

    private static void weight(ContentDraft.Block block, String key, String label, String v) {
        if (v.contains("估")) {
            block.getNotes().add("「" + label + "」是估计值（" + v + "），未写入");
            return;
        }
        Matcher m = NUMBER_UNIT.matcher(v);
        if (!m.find()) {
            unknown(block, label, v);
            return;
        }
        BigDecimal n = new BigDecimal(m.group(1));
        String unit = m.group(2) == null ? "kg" : m.group(2).toLowerCase(Locale.ROOT);
        if ("g".equals(unit) || "克".equals(unit)) {
            n = n.divide(new BigDecimal("1000"), 3, RoundingMode.HALF_UP);
        }
        block.getFields().put(key, n.setScale(3, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString());
    }

    private static void size(ContentDraft.Block block, String label, String v) {
        Matcher m = SIZE_PATTERN.matcher(v);
        if (!m.find()) {
            unknown(block, label, v);
            return;
        }
        String unit = m.group(4) == null ? "mm" : m.group(4).toLowerCase(Locale.ROOT);
        BigDecimal factor = "cm".equals(unit) || "厘米".equals(unit) ? BigDecimal.TEN : BigDecimal.ONE;
        String[] keys = {LENGTH, WIDTH, HEIGHT};
        for (int i = 0; i < 3; i++) {
            BigDecimal n = new BigDecimal(m.group(i + 1)).multiply(factor).setScale(1, RoundingMode.HALF_UP);
            block.getFields().put(keys[i], n.stripTrailingZeros().toPlainString());
        }
    }

    private static void unknown(ContentDraft.Block block, String label, String v) {
        block.getNotes().add("「" + label + "」的「" + v + "」无法识别，未写入");
    }

    /** 括号或逗号之前的部分：「日本（Japan）」→「日本」 */
    private static String head(String v) {
        String[] parts = v.split("[（(，,；;]", 2);
        return parts[0].trim();
    }

    /** 中文或英文国家名、两位国家码 → 两位国家码；认不出为空 */
    static String country(String raw) {
        String h = head(raw);
        if (h.matches("[A-Za-z]{2}") && COUNTRIES.containsValue(h.toUpperCase(Locale.ROOT))) {
            return h.toUpperCase(Locale.ROOT);
        }
        return COUNTRIES.get(h.toLowerCase(Locale.ROOT));
    }

    private static Map<String, String> countries() {
        Map<String, String> m = new LinkedHashMap<>();
        for (String code : Locale.getISOCountries()) {
            Locale l = new Locale("", code);
            m.put(l.getDisplayCountry(Locale.SIMPLIFIED_CHINESE).toLowerCase(Locale.ROOT), code);
            m.put(l.getDisplayCountry(Locale.ENGLISH).toLowerCase(Locale.ROOT), code);
        }
        m.put("台湾", "TW");
        m.put("中国台湾", "TW");
        m.put("美国", "US");
        m.put("usa", "US");
        m.put("uk", "GB");
        m.put("英国", "GB");
        m.put("韩国", "KR");
        m.put("south korea", "KR");
        return m;
    }

    private static String cut(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }
}
