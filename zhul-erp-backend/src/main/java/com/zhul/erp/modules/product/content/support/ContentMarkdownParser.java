package com.zhul.erp.modules.product.content.support;

import com.zhul.erp.modules.product.content.constants.ContentConstants;
import com.zhul.erp.modules.product.support.UrlRules;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 内容包 Markdown 解析（纯函数）：frontmatter 取包类型、平台、品牌、型号、语言，按二级标题分节。
 * 资料包只能是中文；营销包按平台区分，独立站可以是英文或俄文，其他平台只能是英文；
 * 不属于该包类型或该平台的章节跳过并提示。
 * 章节标题按开头匹配，兼容「首屏定义块（40-60词…）」这类带括号说明的标题；正文只有占位说明的视为空。
 */
public final class ContentMarkdownParser {

    private static final Pattern HTML_COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);
    private static final Pattern PLACEHOLDER = Pattern.compile("^[（(].*[）)]$");
    private static final Pattern TABLE_SEPARATOR = Pattern.compile("^:?-{2,}:?$");
    private static final Pattern QUESTION = Pattern.compile("^(?:\\d+[.、)]\\s*)?(?:Q|问|Вопрос)\\s*[:：]\\s*(.*)$");
    private static final Pattern ANSWER = Pattern.compile("^(?:A|答|Ответ)\\s*[:：]\\s*(.*)$");
    private static final Pattern LINK = Pattern.compile("\\[([^\\]]+)]\\(([^)\\s]+)\\)");
    private static final Pattern LIST_MARK = Pattern.compile("^(?:[-*•]\\s*|\\d+[.、)）]\\s*)");

    private ContentMarkdownParser() {
    }

    public static ContentDraft parse(String markdown) {
        ContentDraft draft = new ContentDraft();
        String text = markdown == null ? "" : markdown.replace("\r\n", "\n").replace('\r', '\n');
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        // llm-wiki 商品文件（没有 type）先转换成中文资料包，后面按资料包解析
        String wiki = WikiFactsConverter.convert(text);
        if (wiki != null) {
            text = wiki;
        }
        String[] lines = text.split("\n", -1);
        int i = readFrontmatter(lines, draft);
        String scope = scope(draft);

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
                String normalized = normalize(currentHeading);
                current = ContentSection.match(normalized, scope);
                body.setLength(0);
                skipping = false;
                if (current == null) {
                    skipping = true;
                    if (ContentSection.match(normalized, null) != null) {
                        draft.getNotes().add("「" + currentHeading + "」不属于" + packageName(draft) + "，已跳过");
                    } else {
                        draft.getNotes().add(isLater(currentHeading)
                                ? "「" + currentHeading + "」是第三期内容，已忽略"
                                : "不认识的章节「" + currentHeading + "」，已跳过");
                    }
                } else if (scope == null && !belongs(current, draft.getType())) {
                    skipping = true;
                    draft.getNotes().add("「" + currentHeading + "」不属于" + packageName(draft) + "，已跳过");
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
        checkTypeAndLang(draft);
        if (wiki != null) {
            draft.getNotes().add(0, "按 llm-wiki 商品文件识别为中文资料包");
        }
        if (draft.getBlocks().isEmpty() && draft.getErrors().isEmpty()) {
            draft.getErrors().add("没有解析到任何内容");
        }
        return draft;
    }

    private static void checkTypeAndLang(ContentDraft draft) {
        String type = draft.getType();
        String lang = draft.getLang();
        if (type == null || type.isEmpty()) {
            draft.getErrors().add("缺少包类型（frontmatter 的 type 须为 facts 或 marketing）");
            return;
        }
        boolean facts = ContentConstants.TYPE_FACTS.equals(type);
        if (!facts && !ContentConstants.TYPE_MARKETING.equals(type)) {
            draft.getErrors().add("包类型「" + type + "」无效，须为 facts（资料包）或 marketing（营销包）");
            return;
        }
        if (facts) {
            if (lang == null || lang.isEmpty()) {
                draft.getErrors().add("frontmatter 缺少 lang，资料包须为 zh");
            } else if (!ContentConstants.FACTS_LANG.equals(lang)) {
                draft.getErrors().add("资料包只能是中文（lang: zh）");
            }
            return;
        }
        String code = draft.getPlatform();
        if (code == null || code.isEmpty()) {
            draft.getErrors().add("缺少平台（frontmatter 的 platform 须为 alibaba、mic、site 或 social）");
            return;
        }
        MarketingPlatform platform = MarketingPlatform.of(code);
        if (platform == null) {
            draft.getErrors().add("平台「" + code + "」无效，须为 alibaba（阿里国际站）、mic（中国制造网）、site（独立站）或 social（社媒）");
            return;
        }
        String allowed = platform.langs().size() > 1 ? "英文或俄文（lang: en 或 ru）" : "英文（lang: en）";
        if (lang == null || lang.isEmpty()) {
            draft.getErrors().add("frontmatter 缺少 lang，" + platform.label() + "营销包须为" + allowed);
        } else if (!platform.langs().contains(lang)) {
            draft.getErrors().add(platform.label() + "营销包只能是" + allowed);
        }
    }

    /** 章节过滤范围：资料包为 facts，平台有效的营销包为平台编码；类型或平台无效时为空（文件本身已不能确认） */
    private static String scope(ContentDraft draft) {
        if (ContentConstants.TYPE_FACTS.equals(draft.getType())) {
            return ContentSection.FACTS;
        }
        if (ContentConstants.TYPE_MARKETING.equals(draft.getType()) && MarketingPlatform.of(draft.getPlatform()) != null) {
            return draft.getPlatform();
        }
        return null;
    }

    private static String packageName(ContentDraft draft) {
        if (ContentConstants.TYPE_FACTS.equals(draft.getType())) {
            return "资料包";
        }
        MarketingPlatform p = MarketingPlatform.of(draft.getPlatform());
        return p == null ? "营销包" : p.label() + "营销包";
    }

    /** 平台无效的营销包只排除资料章节；包类型无效时不过滤 */
    private static boolean belongs(ContentSection section, String type) {
        if (ContentConstants.TYPE_MARKETING.equals(type)) {
            return !section.shared();
        }
        return true;
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
            draft.getNotes().stream().skip(1).forEach(block.getNotes()::add);
        }
        return block;
    }

    private static int readFrontmatter(String[] lines, ContentDraft draft) {
        int i = 0;
        while (i < lines.length && lines[i].isBlank()) {
            i++;
        }
        if (i >= lines.length || !"---".equals(lines[i].trim())) {
            draft.getErrors().add("缺少 frontmatter（文件开头用 --- 包住 type、platform、brand、model、lang）");
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
                case "type" -> draft.setType(value.toLowerCase(Locale.ROOT));
                case "platform" -> draft.setPlatform(value.toLowerCase(Locale.ROOT));
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
            case FAQ, TECH_FAQ -> parseFaq(content, block);
            case DOCS -> parseDocs(content, block);
            case KEYWORDS -> parseKeywords(content, block);
            case LOGISTICS, CUSTOMS -> parseFields(content, block);
            case SELLING_POINTS -> parsePoints(content, block);
            default -> parseText(content, raw, block);
        }
        if (block.count() == 0 && block.getErrors().isEmpty()) {
            // 只有表头（模版原样留着）也算没有内容；块里的提示（如无法识别、估计值）挪到文件提示，不丢
            draft.getNotes().add("「" + heading + "」没有可写入的内容，已跳过");
            draft.getNotes().addAll(block.getNotes());
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

    private static void parseText(List<String> content, String raw, ContentDraft.Block block) {
        String text = switch (block.getSection().kind()) {
            // 长描述保留换行（空行去掉）
            case LINES -> String.join("\n", content.stream().map(String::strip).toList());
            // 社媒帖子、商品详情保留原文：换行、空行、表情符号与话题标签都不动，只去掉注释和首尾空白
            case RAW -> HTML_COMMENT.matcher(raw).replaceAll("").strip();
            default -> String.join(" ", content.stream().map(String::strip).toList()).replaceAll("\\s+", " ");
        };
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

    /** 关键词库：「关键词 | 档位」表格，按档位、表格顺序排序；重复的只留第一次；超过上限的截断 */
    private static void parseKeywords(List<String> content, ContentDraft.Block block) {
        List<TableRow> rows = table(content, block);
        if (!rows.isEmpty() && rows.get(0).cells.size() > 1 && tier(rows.get(0).cells.get(1)) == 0
                && normalize(rows.get(0).cells.get(1)).matches("档位|tier|权重|weight")) {
            rows = rows.subList(1, rows.size());
        }
        List<ContentDraft.KeywordRow> parsed = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        List<String> duplicates = new ArrayList<>();
        for (TableRow row : rows) {
            String keyword = row.cells.isEmpty() ? "" : row.cells.get(0);
            if (keyword.isEmpty()) {
                block.getErrors().add("第 " + row.lineNo + " 行缺少关键词");
                continue;
            }
            int tier = row.cells.size() > 1 ? tier(row.cells.get(1)) : 0;
            if (tier == 0) {
                block.getErrors().add("第 " + row.lineNo + " 行档位须为核心、主要或长尾");
                continue;
            }
            check(block, row.lineNo, "关键词", keyword, 128);
            if (!seen.add(keyword.toLowerCase(Locale.ROOT))) {
                duplicates.add(keyword);
                continue;
            }
            parsed.add(new ContentDraft.KeywordRow(keyword, tier));
        }
        // 稳定排序：档位优先，同档保持表格顺序
        parsed.sort(Comparator.comparingInt(ContentDraft.KeywordRow::tier));
        if (!duplicates.isEmpty()) {
            block.getNotes().add("重复的 " + duplicates.size() + " 个关键词已忽略：" + String.join("、", duplicates));
        }
        if (parsed.size() > ContentConstants.MAX_KEYWORDS) {
            block.getNotes().add("关键词超过 " + ContentConstants.MAX_KEYWORDS + " 个，按档位排序后只保留前 "
                    + ContentConstants.MAX_KEYWORDS + " 个，忽略 " + (parsed.size() - ContentConstants.MAX_KEYWORDS) + " 个");
            parsed = new ArrayList<>(parsed.subList(0, ContentConstants.MAX_KEYWORDS));
        }
        block.getKeywords().addAll(parsed);
    }

    /** 档位：核心 1、主要 2、长尾 3，也认英文与数字；无效为 0 */
    private static int tier(String raw) {
        String t = normalize(raw).replace("-", "").replace("_", "");
        return switch (t) {
            case "核心", "核心词", "core", "1" -> ContentConstants.TIER_CORE;
            case "主要", "主要词", "main", "primary", "2" -> ContentConstants.TIER_MAIN;
            case "长尾", "长尾词", "longtail", "3" -> ContentConstants.TIER_LONG_TAIL;
            default -> 0;
        };
    }

    /** 物流、海关：「字段 | 内容」表格，字段名兼容 llm-wiki 写法，值按 FactFields 规范化 */
    private static void parseFields(List<String> content, ContentDraft.Block block) {
        for (TableRow row : table(content, block)) {
            if (row.cells.isEmpty() || row.cells.get(0).isEmpty() || "字段".equals(row.cells.get(0))) {
                continue;
            }
            FactFields.put(block, row.cells.get(0), row.cells.size() > 1 ? row.cells.get(1) : "");
        }
    }

    /** 卖点：每行一条，去掉列表符号和序号；超过上限的截断 */
    private static void parsePoints(List<String> content, ContentDraft.Block block) {
        int lineNo = 0;
        List<String> points = new ArrayList<>();
        for (String line : content) {
            lineNo++;
            String t = LIST_MARK.matcher(line.strip()).replaceFirst("").strip();
            if (t.isEmpty()) {
                continue;
            }
            check(block, lineNo, "卖点", t, 128);
            points.add(t);
        }
        if (points.size() > ContentConstants.MAX_SELLING_POINTS) {
            block.getNotes().add("卖点超过 " + ContentConstants.MAX_SELLING_POINTS + " 条，只保留前 "
                    + ContentConstants.MAX_SELLING_POINTS + " 条，忽略 " + (points.size() - ContentConstants.MAX_SELLING_POINTS) + " 条");
            points = points.subList(0, ContentConstants.MAX_SELLING_POINTS);
        }
        block.getPoints().addAll(points);
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
