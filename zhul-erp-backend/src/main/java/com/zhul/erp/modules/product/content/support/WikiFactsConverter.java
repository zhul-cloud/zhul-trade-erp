package com.zhul.erp.modules.product.content.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * llm-wiki 商品文件 → 中文资料包（spec product/product-content「上传 wiki 商品文件作为资料包」）。
 * 识别条件：frontmatter 没有 type、有 brand 与 model，正文有「基本信息」章节（资料包模版没有这一章，避免误认）。
 * 取一级标题「—」后的说明作规格摘要、frontmatter 的 series 作系列、「规格参数」下所有小节表格作规格，
 * 「物流信息」「海关信息」「物流与海关」表格原样带过去（字段名由 FactFields 识别）。其他章节不取。
 */
public final class WikiFactsConverter {

    private WikiFactsConverter() {
    }

    /** 单台设备的信息，不是商品资料 */
    private static final Set<String> INSTANCE_LABELS = Set.of("序列号", "生产日期", "制造日期", "出厂日期", "批号",
            "s/n", "serialnumber", "pcb编号", "出货信息");
    private static final Set<String> HEADER_LABELS = Set.of("规格", "字段", "参数项", "项目");

    /** 是 wiki 商品文件时返回转换后的资料包文本，否则为空 */
    public static String convert(String text) {
        String[] lines = text.split("\n", -1);
        int i = 0;
        while (i < lines.length && lines[i].isBlank()) {
            i++;
        }
        if (i >= lines.length || !"---".equals(lines[i].trim())) {
            return null;
        }
        String brand = null;
        String model = null;
        String series = null;
        boolean hasType = false;
        int body = -1;
        for (int j = i + 1; j < lines.length; j++) {
            String line = lines[j].trim();
            if ("---".equals(line)) {
                body = j + 1;
                break;
            }
            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String key = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = value(line.substring(colon + 1));
            switch (key) {
                case "type" -> hasType = true;
                case "brand" -> brand = value;
                case "model" -> model = value;
                case "series" -> series = value;
                default -> {
                    // 其他字段不取
                }
            }
        }
        if (hasType || brand == null || model == null || body < 0) {
            return null;
        }
        boolean wiki = false;
        for (int j = body; j < lines.length; j++) {
            String t = lines[j].trim();
            if (t.startsWith("## 基本信息")) {
                wiki = true;
                break;
            }
        }
        if (!wiki) {
            return null;
        }

        String summary = null;
        List<String[]> specs = new ArrayList<>();
        List<String[]> logistics = new ArrayList<>();
        List<String[]> customs = new ArrayList<>();
        String section = "";
        for (int j = body; j < lines.length; j++) {
            String t = lines[j].trim();
            if (summary == null && t.startsWith("# ")) {
                summary = titleDescription(t.substring(2));
                continue;
            }
            if (t.startsWith("## ")) {
                section = t.substring(3).trim();
                continue;
            }
            if (!t.startsWith("|")) {
                continue;
            }
            String[] cells = cells(t);
            if (cells.length < 2 || cells[0].isEmpty() || cells[0].matches(":?-{2,}:?")) {
                continue;
            }
            if (section.startsWith("规格参数")) {
                String label = cells[0];
                String norm = label.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
                if (HEADER_LABELS.contains(norm) || INSTANCE_LABELS.stream().anyMatch(norm::startsWith)
                        || empty(cells[1])) {
                    continue;
                }
                specs.add(new String[] {cut(label, 64), cut(cells[1], 256)});
            } else if (section.startsWith("物流") || section.startsWith("海关")) {
                ContentSection target = FactFields.sectionOf(cells[0]);
                if (target == ContentSection.LOGISTICS) {
                    logistics.add(cells);
                } else if (target == ContentSection.CUSTOMS) {
                    customs.add(cells);
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("---\ntype: facts\nbrand: ").append(brand).append("\nmodel: ").append(model).append("\nlang: zh\n---\n\n");
        if (summary != null) {
            sb.append("## 一句话规格摘要\n").append(summary).append("\n\n");
        }
        if (series != null) {
            sb.append("## 系列\n").append(series).append("\n\n");
        }
        table(sb, "规格参数表", specs);
        table(sb, "物流信息", logistics);
        table(sb, "海关信息", customs);
        return sb.toString();
    }

    private static void table(StringBuilder sb, String heading, List<String[]> rows) {
        if (rows.isEmpty()) {
            return;
        }
        sb.append("## ").append(heading).append("\n| 字段 | 内容 |\n|---|---|\n");
        for (String[] r : rows) {
            sb.append("| ").append(r[0]).append(" | ").append(r[1]).append(" |\n");
        }
        sb.append('\n');
    }

    private static String titleDescription(String title) {
        for (String sep : new String[] {"—", " - ", "–"}) {
            int at = title.indexOf(sep);
            if (at >= 0) {
                String d = title.substring(at + sep.length()).trim();
                return empty(d) ? null : cut(d, 300);
            }
        }
        return null;
    }

    private static String[] cells(String row) {
        String inner = row.substring(1);
        if (inner.endsWith("|")) {
            inner = inner.substring(0, inner.length() - 1);
        }
        String[] parts = inner.split("\\|", -1);
        String[] out = new String[parts.length];
        for (int k = 0; k < parts.length; k++) {
            out[k] = parts[k].trim();
        }
        return out;
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
        return empty(v) ? null : v;
    }

    private static boolean empty(String v) {
        return v == null || v.isBlank() || "—".equals(v.trim()) || "-".equals(v.trim()) || v.contains("[待确认]");
    }

    private static String cut(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }
}
