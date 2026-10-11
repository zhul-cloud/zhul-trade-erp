package com.zhul.erp.modules.product.candidate.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * llm-wiki 商品文件解析（纯函数，spec product/product-candidate「从商品文件导入候选」）：
 * frontmatter 取 brand、model、category；「基本信息」表格取品类、中文产品名、英文产品名；一级标题「—」之后取中文描述。
 * 空值、「—」「-」「[待确认]」视为没有；只取建候选需要的字段，规格等资料不读。
 */
public final class WikiProductParser {

    private WikiProductParser() {
    }

    /** 解析结果；errors 不为空时不能导入 */
    public record Draft(String brand, String model, String categoryText, String productName, String description,
                        String descriptionEn, List<String> errors) {
    }

    public static Draft parse(String markdown) {
        String text = markdown == null ? "" : markdown.replace("\r\n", "\n").replace('\r', '\n');
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        String[] lines = text.split("\n", -1);
        List<String> errors = new ArrayList<>();
        String brand = null;
        String model = null;
        String category = null;
        int i = 0;
        while (i < lines.length && lines[i].isBlank()) {
            i++;
        }
        if (i >= lines.length || !"---".equals(lines[i].trim())) {
            errors.add("缺少 frontmatter，不是商品文件");
            return new Draft(null, null, null, null, null, null, errors);
        }
        boolean closed = false;
        for (i = i + 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if ("---".equals(line)) {
                closed = true;
                i++;
                break;
            }
            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String key = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = value(line.substring(colon + 1));
            switch (key) {
                case "brand" -> brand = value;
                case "model" -> model = value;
                case "category" -> category = value;
                default -> {
                    // 其他字段（series、hs_code、website_url 等）建候选时不用
                }
            }
        }
        if (!closed) {
            errors.add("frontmatter 没有结束的 ---");
        }
        if (model == null) {
            errors.add("缺少型号（frontmatter 的 model），不是商品文件");
        }
        if (brand == null) {
            errors.add("缺少品牌（frontmatter 的 brand）");
        }

        String title = null;
        String tableCategory = null;
        String nameZh = null;
        String nameEn = null;
        boolean inBasic = false;
        for (; i < lines.length; i++) {
            String line = lines[i].trim();
            if (title == null && line.startsWith("# ")) {
                title = line.substring(2).trim();
                continue;
            }
            if (line.startsWith("## ")) {
                inBasic = line.substring(3).trim().startsWith("基本信息");
                continue;
            }
            if (!inBasic || !line.startsWith("|")) {
                continue;
            }
            String[] cells = line.substring(1).split("\\|", -1);
            if (cells.length < 2) {
                continue;
            }
            String field = cells[0].trim();
            String content = clean(cells[1]);
            switch (field) {
                case "品类" -> tableCategory = content;
                case "中文产品名" -> nameZh = content;
                case "英文产品名" -> nameEn = content;
                default -> {
                    // 其他行不用
                }
            }
        }
        return new Draft(brand, model, categoryText(category, tableCategory), nameZh, titleDescription(title), nameEn,
                errors);
    }

    /** 品类文字：frontmatter 的驼峰写法拆成单词，再接上表格里的品类（含括号里的中文），交给品类匹配 */
    static String categoryText(String category, String tableCategory) {
        List<String> parts = new ArrayList<>(2);
        if (category != null) {
            parts.add(category.replaceAll("([a-z])([A-Z])", "$1 $2").replaceAll("([A-Z]{2,})([A-Z][a-z])", "$1 $2"));
        }
        if (tableCategory != null && !tableCategory.equalsIgnoreCase(category)) {
            parts.add(tableCategory);
        }
        return parts.isEmpty() ? null : String.join(" ", parts);
    }

    /** 一级标题「品牌 型号 — 说明」里「—」之后的说明 */
    static String titleDescription(String title) {
        if (title == null) {
            return null;
        }
        for (String sep : new String[] {"—", " - ", "–"}) {
            int at = title.indexOf(sep);
            if (at >= 0) {
                return clean(title.substring(at + sep.length()));
            }
        }
        return null;
    }

    private static String value(String raw) {
        String v = raw;
        int hash = v.indexOf(" #");
        if (hash >= 0) {
            v = v.substring(0, hash);
        }
        v = v.trim();
        if (v.length() >= 2 && (v.startsWith("\"") && v.endsWith("\"") || v.startsWith("'") && v.endsWith("'"))) {
            v = v.substring(1, v.length() - 1).trim();
        }
        return clean(v);
    }

    /** 空、「—」「-」、含「[待确认]」的值视为没有 */
    static String clean(String raw) {
        if (raw == null) {
            return null;
        }
        String v = raw.trim();
        if (v.isEmpty() || "—".equals(v) || "-".equals(v) || "–".equals(v) || v.contains("[待确认]")) {
            return null;
        }
        return v;
    }
}
