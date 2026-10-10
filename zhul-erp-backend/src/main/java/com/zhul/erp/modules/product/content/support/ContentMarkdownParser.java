package com.zhul.erp.modules.product.content.support;

import com.zhul.erp.modules.product.support.UrlRules;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 内容包 Markdown 解析（纯函数，design.md 决策 3）：frontmatter 取品牌、型号、语言，按二级标题分节。
 * 章节标题按开头匹配，兼容「首屏定义块（40-60词…）」这类带括号说明的标题；正文只有占位说明的视为空。
 */
public final class ContentMarkdownParser {

    public static final Set<String> LANGS = Set.of("zh", "en", "ru");

    private static final Pattern HTML_COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);
    private static final Pattern PLACEHOLDER = Pattern.compile("^[（(].*[）)]$");
    private static final Pattern TABLE_SEPARATOR = Pattern.compile("^:?-{2,}:?$");
    private static final Pattern QUESTION = Pattern.compile("^(?:\\d+[.、)]\\s*)?(?:Q|问|Вопрос)\\s*[:：]\\s*(.*)$");
    private static final Pattern ANSWER = Pattern.compile("^(?:A|答|Ответ)\\s*[:：]\\s*(.*)$");
    private static final Pattern LINK = Pattern.compile("\\[([^\\]]+)]\\(([^)\\s]+)\\)");

    private ContentMarkdownParser() {
    }

    public static ContentDraft parse(String markdown) {
        ContentDraft draft = new ContentDraft();
        String text = markdown == null ? "" : markdown.replace("\r\n", "\n").replace('\r', '\n');
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        String[] lines = text.split("\n", -1);
        int i = readFrontmatter(lines, draft);

        ContentSection current = null;
        String currentHeading = null;
        StringBuilder body = new StringBuilder();
        boolean skipping = true;
        for (; i <= lines.length; i++) {
            String line = i < lines.length ? lines[i] : null;
            boolean heading = line != null && line.startsWith("## ");
            if (line == null || heading) {
                if (!skipping) {
                    closeSection(draft, current, currentHeading, body.toString());
                }
                if (line == null) {
                    break;
                }
                currentHeading = line.substring(3).trim();
                current = ContentSection.match(normalize(currentHeading));
                body.setLength(0);
                skipping = false;
                if (current == null) {
                    skipping = true;
                    draft.getNotes().add(isLater(currentHeading)
                            ? "「" + currentHeading + "」是第三期内容，已忽略"
                            : "不认识的章节「" + currentHeading + "」，已跳过");
                }
                continue;
            }
            if (!skipping) {
                body.append(line).append('\n');
            }
        }

        if (draft.getBrand() == null || draft.getBrand().isEmpty()) {
            draft.getErrors().add("frontmatter 缺少 brand（品牌）");
        }
        if (draft.getModel() == null || draft.getModel().isEmpty()) {
            draft.getErrors().add("frontmatter 缺少 model（型号）");
        }
        if (draft.getLang() == null || draft.getLang().isEmpty()) {
            draft.getErrors().add("frontmatter 缺少 lang（语言），须为 zh、en、ru 之一");
        } else if (!LANGS.contains(draft.getLang())) {
            draft.getErrors().add("语言「" + draft.getLang() + "」无效，须为 zh、en、ru 之一");
        }
        if (draft.getBlocks().isEmpty() && draft.getErrors().isEmpty()) {
            draft.getErrors().add("没有解析到任何内容");
        }
        return draft;
    }

    /** 只解析一个章节的正文（预览里修改某块后重新解析用） */
    public static ContentDraft.Block parseBlock(ContentSection section, String raw) {
        ContentDraft draft = new ContentDraft();
        closeSection(draft, section, section.label(), raw == null ? "" : raw);
        ContentDraft.Block block = draft.getBlocks().get(section);
        if (block == null) {
            block = new ContentDraft.Block();
            block.setSection(section);
            block.setRaw(raw == null ? "" : raw);
        }
        return block;
    }

    private static int readFrontmatter(String[] lines, ContentDraft draft) {
        int i = 0;
        while (i < lines.length && lines[i].isBlank()) {
            i++;
        }
        if (i >= lines.length || !"---".equals(lines[i].trim())) {
            draft.getErrors().add("缺少 frontmatter（文件开头用 --- 包住 brand、model、lang）");
            return i;
        }
        for (int j = i + 1; j < lines.length; j++) {
            String line = lines[j].trim();
            if ("---".equals(line)) {
                return j + 1;
            }
            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String key = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = stripComment(line.substring(colon + 1)).trim();
            if (value.length() >= 2 && (value.startsWith("\"") && value.endsWith("\"")
                    || value.startsWith("'") && value.endsWith("'"))) {
                value = value.substring(1, value.length() - 1).trim();
            }
            switch (key) {
                case "brand" -> draft.setBrand(value);
                case "model" -> draft.setModel(value);
                case "lang" -> draft.setLang(value.toLowerCase(Locale.ROOT));
                default -> {
                    // 其他键（series、category、status 等）不使用
                }
            }
        }
        draft.getErrors().add("frontmatter 没有结束的 ---");
        return lines.length;
    }

    private static String stripComment(String value) {
        int hash = value.indexOf(" #");
        return hash >= 0 ? value.substring(0, hash) : value;
    }

    private static void closeSection(ContentDraft draft, ContentSection section, String heading, String raw) {
        List<String> content = contentLines(raw);
        if (content.isEmpty()) {
            draft.getNotes().add("「" + heading + "」没有内容，已跳过");
            return;
        }
        if (draft.getBlocks().containsKey(section)) {
            draft.getNotes().add("「" + section.label() + "」出现了多次，以最后一次为准");
        }
        ContentDraft.Block block = new ContentDraft.Block();
        block.setSection(section);
        block.setRaw(raw.strip());
        switch (section) {
            case SPECS -> parseSpecs(content, block);
            case APPS -> parseApps(content, block);
            case COMPAT -> parseCompat(content, block);
            case FAQ -> parseFaq(content, block);
            case DOCS -> parseDocs(content, block);
            default -> parseText(content, block);
        }
        if (block.count() == 0 && block.getErrors().isEmpty()) {
            // 只有表头（模版原样留着）也算没有内容
            draft.getNotes().add("「" + heading + "」没有内容，已跳过");
            return;
        }
        draft.getBlocks().put(section, block);
    }

    /** 去掉 HTML 注释与空行；全部是占位说明时返回空 */
    private static List<String> contentLines(String raw) {
        String cleaned = HTML_COMMENT.matcher(raw).replaceAll("");
        List<String> result = new ArrayList<>();
        boolean allPlaceholder = true;
        for (String line : cleaned.split("\n")) {
            String t = line.strip();
            if (t.isEmpty()) {
                continue;
            }
            result.add(line.stripTrailing());
            if (!PLACEHOLDER.matcher(t).matches()) {
                allPlaceholder = false;
            }
        }
        return allPlaceholder ? List.of() : result;
    }

    private static void parseText(List<String> content, ContentDraft.Block block) {
        String text;
        if (block.getSection() == ContentSection.LONG_DESC) {
            // 长描述保留换行（空行去掉）
            text = String.join("\n", content.stream().map(String::strip).toList());
        } else {
            text = String.join(" ", content.stream().map(String::strip).toList()).replaceAll("\\s+", " ");
        }
        block.setText(text);
        if (text.length() > block.getSection().maxLength()) {
            block.getErrors().add(block.getSection().label() + "超过 " + block.getSection().maxLength()
                    + " 字（当前 " + text.length() + " 字）");
        }
    }

    private static void parseSpecs(List<String> content, ContentDraft.Block block) {
        for (TableRow row : table(content, block)) {
            if (row.cells.size() < 2 || row.cells.get(0).isEmpty() || row.cells.get(1).isEmpty()) {
                block.getErrors().add("第 " + row.lineNo + " 行须为「字段 | 内容」，可选第三列单位");
                continue;
            }
            String label = row.cells.get(0);
            String value = row.cells.get(1);
            String unit = row.cells.size() > 2 ? row.cells.get(2) : "";
            check(block, row.lineNo, "字段", label, 64);
            check(block, row.lineNo, "内容", value, 256);
            check(block, row.lineNo, "单位", unit, 32);
            block.getSpecs().add(new ContentDraft.SpecRow(label, value, unit));
        }
    }

    private static void parseCompat(List<String> content, ContentDraft.Block block) {
        for (TableRow row : table(content, block)) {
            if (row.cells.isEmpty() || row.cells.get(0).isEmpty()) {
                block.getErrors().add("第 " + row.lineNo + " 行须为「原型号 | 兼容说明」");
                continue;
            }
            String model = row.cells.get(0);
            String note = row.cells.size() > 1 ? row.cells.get(1) : "";
            check(block, row.lineNo, "原型号", model, 128);
            check(block, row.lineNo, "兼容说明", note, 500);
            block.getCompat().add(new ContentDraft.CompatRow(model, note));
        }
    }

    private static void parseApps(List<String> content, ContentDraft.Block block) {
        int lineNo = 0;
        for (String line : content) {
            lineNo++;
            String t = line.strip();
            if (!t.startsWith("-") && !t.startsWith("*")) {
                block.getErrors().add("第 " + lineNo + " 行须为「- 图标 | 标题 | 描述」");
                continue;
            }
            String[] parts = t.substring(1).split("\\|", -1);
            String icon = "";
            String title;
            String desc = "";
            if (parts.length >= 3) {
                icon = parts[0].strip();
                title = parts[1].strip();
                desc = joinRest(parts, 2);
            } else if (parts.length == 2) {
                title = parts[0].strip();
                desc = parts[1].strip();
            } else {
                title = parts[0].strip();
            }
            if (title.isEmpty()) {
                block.getErrors().add("第 " + lineNo + " 行缺少标题");
                continue;
            }
            check(block, lineNo, "图标", icon, 16);
            check(block, lineNo, "标题", title, 64);
            check(block, lineNo, "描述", desc, 500);
            block.getApps().add(new ContentDraft.AppRow(icon, title, desc));
        }
    }

    private static void parseFaq(List<String> content, ContentDraft.Block block) {
        String question = null;
        StringBuilder answer = null;
        int qLine = 0;
        int lineNo = 0;
        for (String line : content) {
            lineNo++;
            String t = line.strip();
            Matcher q = QUESTION.matcher(t);
            Matcher a = ANSWER.matcher(t);
            if (q.matches()) {
                addFaq(block, question, answer, qLine);
                question = q.group(1).strip();
                answer = null;
                qLine = lineNo;
            } else if (a.matches() && question != null && answer == null) {
                answer = new StringBuilder(a.group(1).strip());
            } else if (answer != null) {
                answer.append(answer.length() == 0 ? "" : " ").append(t);
            } else if (question != null) {
                question = question + " " + t;
            } else {
                block.getErrors().add("第 " + lineNo + " 行不在任何「Q:」之下");
            }
        }
        addFaq(block, question, answer, qLine);
    }

    private static void addFaq(ContentDraft.Block block, String question, StringBuilder answer, int lineNo) {
        if (question == null) {
            return;
        }
        if (answer == null || answer.length() == 0) {
            block.getErrors().add("第 " + lineNo + " 行的问题缺少「A:」答案");
            return;
        }
        if (question.isEmpty()) {
            block.getErrors().add("第 " + lineNo + " 行缺少问题");
            return;
        }
        check(block, lineNo, "问题", question, 256);
        check(block, lineNo, "答案", answer.toString(), 5000);
        block.getFaqs().add(new ContentDraft.FaqRow(question, answer.toString()));
    }

    private static void parseDocs(List<String> content, ContentDraft.Block block) {
        int lineNo = 0;
        for (String line : content) {
            lineNo++;
            Matcher m = LINK.matcher(line);
            if (!m.find()) {
                block.getErrors().add("第 " + lineNo + " 行须为「- [标题](地址)」");
                continue;
            }
            String title = m.group(1).strip();
            String url = m.group(2).strip();
            if (!UrlRules.isSafe(url)) {
                block.getErrors().add("第 " + lineNo + " 行地址须为 http(s) 或站内路径：" + url);
                continue;
            }
            check(block, lineNo, "标题", title, 128);
            check(block, lineNo, "地址", url, 512);
            block.getDocs().add(new ContentDraft.DocRow(title, url));
        }
    }

    private record TableRow(int lineNo, List<String> cells) {
    }

    /** 解析 Markdown 表格：有分隔行时分隔行之前是表头；非表格行报错 */
    private static List<TableRow> table(List<String> content, ContentDraft.Block block) {
        List<TableRow> rows = new ArrayList<>();
        int separatorAt = -1;
        int lineNo = 0;
        for (String line : content) {
            lineNo++;
            String t = line.strip();
            if (!t.startsWith("|")) {
                block.getErrors().add("第 " + lineNo + " 行不是表格行（须以 | 开头）");
                continue;
            }
            List<String> cells = cells(t);
            if (!cells.isEmpty() && cells.stream().allMatch(c -> TABLE_SEPARATOR.matcher(c).matches())) {
                if (separatorAt < 0) {
                    separatorAt = rows.size();
                }
                continue;
            }
            rows.add(new TableRow(lineNo, cells));
        }
        return separatorAt > 0 ? rows.subList(separatorAt, rows.size()) : rows;
    }

    private static List<String> cells(String row) {
        String inner = row.substring(1);
        if (inner.endsWith("|")) {
            inner = inner.substring(0, inner.length() - 1);
        }
        List<String> cells = new ArrayList<>();
        for (String c : inner.split("\\|", -1)) {
            cells.add(c.strip());
        }
        return cells;
    }

    private static String joinRest(String[] parts, int from) {
        StringBuilder sb = new StringBuilder();
        for (int k = from; k < parts.length; k++) {
            if (k > from) {
                sb.append('|');
            }
            sb.append(parts[k]);
        }
        return sb.toString().strip();
    }

    private static void check(ContentDraft.Block block, int lineNo, String field, String value, int max) {
        if (value.length() > max) {
            block.getErrors().add("第 " + lineNo + " 行" + field + "超过 " + max + " 字");
        }
    }

    private static String normalize(String heading) {
        return heading.replaceAll("\\s+", "").replaceFirst("^\\d+[.、]", "").toLowerCase(Locale.ROOT);
    }

    private static boolean isLater(String heading) {
        String n = normalize(heading);
        return ContentSection.LATER.stream().anyMatch(n::startsWith);
    }
}
