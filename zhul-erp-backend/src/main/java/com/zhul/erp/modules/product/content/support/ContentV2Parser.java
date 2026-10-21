package com.zhul.erp.modules.product.content.support;

import com.zhul.erp.modules.product.content.constants.ContentConstants;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Currency;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 商品内容规范 v2 解析（纯函数，见 docs/02-产品PRD/02-商品域/商品内容规范-v2.md）。
 * 资料包解析成一项；营销包按「关键词库 + 各平台二级标题」拆成多项，每项一个 ContentDraft（part 区分），
 * 后面的预览、确认、上传记录按项处理。「## 内部附录」及之后的内容整段跳过。
 */
public final class ContentV2Parser {

    /** 营销包拆出的关键词库项 */
    public static final String PART_KEYWORDS = "keywords";
    /** 英文营销文件里的「## 独立站俄文」拆出的项：平台为独立站，语言为俄文 */
    public static final String PART_SITE_RU = "site-ru";

    /** 产品识别字段键 */
    public static final String NAME_ZH = "name_zh";
    public static final String NAME_EN = "name_en";
    public static final String SERIES = "series";
    /** 独立站 SEO 字段键 */
    public static final String SEO_TITLE = "seo_title";
    public static final String META_DESCRIPTION = "meta_description";
    public static final String H1 = "h1";
    public static final String URL_SLUG = "url_slug";

    public static final String CHANNEL_LINKEDIN = "LinkedIn";
    public static final String CHANNEL_FACEBOOK = "Facebook";
    public static final String CHANNEL_YOUTUBE = "YouTube";

    private static final Pattern HTML_COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);
    private static final Pattern LINK = Pattern.compile("\\[([^\\]]*)]\\(([^)\\s]+)\\)");
    private static final Pattern BOLD_ITEM = Pattern.compile("^(?:\\d+[.、)）]|[-*•])\\s*\\*\\*(.+?)\\*\\*\\s*(.*)$");
    /** 编号写在加粗里的问题：**1. What is …?**（答案在下一段） */
    private static final Pattern BOLD_NUMBERED = Pattern.compile("^\\*\\*\\s*\\d+[.、)）]\\s*(.+?)\\*\\*\\s*(.*)$");
    private static final Pattern MONEY = Pattern.compile("\\d[\\d,]*(?:\\.\\d+)?");
    /** 商品平台参考价的上限，与 product_reference_price 的 decimal(18,2)、decimal(18,6) 一致 */
    private static final BigDecimal AMOUNT_LIMIT = new BigDecimal("10000000000000000");
    private static final BigDecimal RATE_LIMIT = new BigDecimal("1000000000000");
    private static final Set<String> ISO_CURRENCIES = Currency.getAvailableCurrencies().stream()
            .map(Currency::getCurrencyCode).collect(Collectors.toUnmodifiableSet());
    /**
     * 中国制造网（工业产品页版式）用加粗二级标题写的四个区块，归回前面的平台章节：
     * Technical Specifications → 产品属性、Key Features and Benefits → 卖点、Product Details → 产品详情、FAQ → FAQ
     */
    private static final Map<String, String> PAGE_BLOCKS = Map.of(
            "technicalspecifications", "产品属性", "keyfeaturesandbenefits", "卖点", "keyfeatures", "卖点",
            "productdetails", "产品详情", "faq", "FAQ");
    private static final Pattern NUMBERED = Pattern.compile("^\\d+[.、)）]\\s*(.*)$");
    private static final Pattern SOURCE_CODE = Pattern.compile("S\\d{1,3}");
    private static final Pattern PRIORITY = Pattern.compile("^p([123])$");

    private static final Map<String, Integer> SOCIAL_LIMITS = Map.of(CHANNEL_LINKEDIN, 3000, CHANNEL_FACEBOOK, 5000,
            CHANNEL_YOUTUBE, 10000);

    private ContentV2Parser() {
    }

    /** 上传入口：v2 文件按本规范拆项解析；没有 schema: 2 的按旧格式（含 wiki 商品文件）解析成一项 */
    public static List<ContentDraft> parseAll(String markdown) {
        String text = clean(markdown);
        if (isV2(text)) {
            return parse(text);
        }
        ContentDraft draft = ContentMarkdownParser.parse(markdown);
        // llm-wiki 商品文件已有自己的识别提示，不再提示旧格式
        if (draft.getNotes().isEmpty() || !draft.getNotes().get(0).contains("llm-wiki")) {
            draft.getNotes().add(0, "旧格式（frontmatter 没有 schema: 2），按旧规则解析");
        }
        return List.of(draft);
    }

    static boolean isV2(String text) {
        ContentDraft head = new ContentDraft();
        ContentMarkdownParser.readFrontmatter(text.split("\n", -1), head);
        return head.getSchema() != null && head.getSchema() == 2;
    }

    private static String clean(String markdown) {
        String text = markdown == null ? "" : markdown.replace("\r\n", "\n").replace('\r', '\n');
        return text.startsWith("﻿") ? text.substring(1) : text;
    }

    public static List<ContentDraft> parse(String markdown) {
        String[] lines = clean(markdown).split("\n", -1);
        ContentDraft head = new ContentDraft();
        int start = ContentMarkdownParser.readFrontmatter(lines, head);
        List<Section> sections = sections(lines, start, "## ");
        List<String> fileNotes = new ArrayList<>();
        int appendix = -1;
        for (int i = 0; i < sections.size(); i++) {
            if (normalize(sections.get(i).heading).startsWith("内部附录")) {
                appendix = i;
                break;
            }
        }
        if (appendix >= 0) {
            sections = sections.subList(0, appendix);
        }
        sections = foldPageBlocks(sections);

        List<ContentDraft> drafts = new ArrayList<>();
        String type = head.getType();
        if (ContentConstants.TYPE_FACTS.equals(type)) {
            ContentDraft d = copyHead(head, null, null);
            parseFacts(d, sections);
            checkLang(d, ContentConstants.FACTS_LANG.equals(d.getLang()), "资料包只能是中文（lang: zh）");
            drafts.add(d);
        } else if (ContentConstants.TYPE_MARKETING.equals(type)) {
            parseMarketing(head, sections, drafts, fileNotes);
            if (drafts.isEmpty()) {
                ContentDraft d = copyHead(head, null, null);
                d.getErrors().add("没有解析到关键词库或任何平台章节（## 阿里国际站、## 中国制造网、## 独立站、## 社媒）");
                drafts.add(d);
            }
        } else {
            ContentDraft d = copyHead(head, null, null);
            d.getErrors().add(type == null || type.isEmpty()
                    ? "缺少包类型（frontmatter 的 type 须为 facts 或 marketing）"
                    : "包类型「" + type + "」无效，须为 facts（资料包）或 marketing（营销包）");
            drafts.add(d);
        }
        drafts.get(0).getNotes().addAll(0, fileNotes);
        for (ContentDraft d : drafts) {
            if (d.getBrand() == null || d.getBrand().isEmpty()) {
                d.getErrors().add("frontmatter 缺少 brand（品牌）");
            }
            if (d.getModel() == null || d.getModel().isEmpty()) {
                d.getErrors().add("frontmatter 缺少 model（型号）");
            }
            if (d.getBlocks().isEmpty() && d.getErrors().isEmpty()) {
                d.getErrors().add("没有解析到任何内容");
            }
        }
        return drafts;
    }

    private static ContentDraft copyHead(ContentDraft head, String platform, String part) {
        ContentDraft d = new ContentDraft();
        d.setBrand(head.getBrand());
        d.setModel(head.getModel());
        d.setLang(head.getLang());
        d.setType(head.getType());
        d.setUpdated(head.getUpdated());
        d.setSchema(2);
        d.setPlatform(platform);
        d.setPart(part);
        d.getErrors().addAll(head.getErrors());
        return d;
    }

    private static void checkLang(ContentDraft d, boolean ok, String message) {
        if (d.getLang() == null || d.getLang().isEmpty()) {
            d.getErrors().add("frontmatter 缺少 lang");
        } else if (!ok) {
            d.getErrors().add(message);
        }
    }

    // ---------------------------------------------------------------- 资料包

    /**
     * 平台章节后面紧跟的工业产品页区块（## **Technical Specifications** 等）不是新章节，
     * 改写成三级标题并入前面的阿里国际站或中国制造网章节；区块里的三级标题（如询盘引导语）随之留在该平台下
     */
    private static List<Section> foldPageBlocks(List<Section> sections) {
        List<Section> result = new ArrayList<>(sections.size());
        for (Section s : sections) {
            String item = PAGE_BLOCKS.get(normalize(s.heading).replaceAll("[^a-z]", ""));
            Section last = result.isEmpty() ? null : result.get(result.size() - 1);
            MarketingPlatform owner = last == null ? null : platformOf(normalize(last.heading));
            if (item != null && (owner == MarketingPlatform.MIC || owner == MarketingPlatform.ALIBABA)) {
                result.set(result.size() - 1, new Section(last.heading, last.body + "\n### " + item + "\n" + s.body));
                continue;
            }
            result.add(s);
        }
        return result;
    }

    private static void parseFacts(ContentDraft d, List<Section> sections) {
        for (Section s : sections) {
            String n = normalize(s.heading);
            ContentSection section = factSection(n);
            if (section == null) {
                d.getNotes().add(n.startsWith("价格") || n.startsWith("包装说明") || n.startsWith("产品图片")
                        ? "「" + s.heading + "」不导入，已跳过"
                        : "不认识的章节「" + s.heading + "」，已跳过");
                continue;
            }
            put(d, section, s.heading, s.body);
        }
    }

    private static ContentSection factSection(String n) {
        if (n.startsWith("产品识别")) {
            return ContentSection.IDENTITY;
        }
        if (n.startsWith("一句话规格摘要") || n.startsWith("规格摘要")) {
            return ContentSection.SUMMARY;
        }
        if (n.startsWith("规格参数") || n.startsWith("核心技术参数")) {
            return ContentSection.SPECS;
        }
        if (n.startsWith("关键未知字段")) {
            return ContentSection.UNKNOWNS;
        }
        if (n.startsWith("应用需求") || n.startsWith("应用场景")) {
            return ContentSection.APPS;
        }
        if (n.startsWith("兼容")) {
            return ContentSection.COMPAT;
        }
        if (n.startsWith("技术问答")) {
            return ContentSection.TECH_FAQ;
        }
        if (n.startsWith("技术资料")) {
            return ContentSection.DOCS;
        }
        if (n.startsWith("物流")) {
            return ContentSection.LOGISTICS;
        }
        if (n.startsWith("海关")) {
            return ContentSection.CUSTOMS;
        }
        if (n.startsWith("来源索引")) {
            return ContentSection.SOURCES;
        }
        if (n.startsWith("冲突与待确认") || n.startsWith("冲突")) {
            return ContentSection.CONFLICTS;
        }
        if (n.startsWith("商品平台参考价")) {
            return ContentSection.CARD_PRICE;
        }
        if (n.startsWith("平台参考价") || n.startsWith("参考价")) {
            return ContentSection.LISTING_PRICES;
        }
        return null;
    }

    // ---------------------------------------------------------------- 营销包

    private static void parseMarketing(ContentDraft head, List<Section> sections, List<ContentDraft> drafts,
                                       List<String> fileNotes) {
        boolean ru = "ru".equals(head.getLang());
        Set<String> seen = new HashSet<>();
        for (Section s : sections) {
            String n = normalize(s.heading);
            if (n.startsWith("关键词库") || n.startsWith("关键词")) {
                ContentDraft d = copyHead(head, null, PART_KEYWORDS);
                if (!seen.add(PART_KEYWORDS)) {
                    fileNotes.add("「关键词库」出现了多次，只用第一次");
                    continue;
                }
                checkLang(d, "en".equals(d.getLang()), "关键词库只能是英文（lang: en）；俄文文件只写独立站");
                put(d, ContentSection.KEYWORD_LIBRARY, s.heading, s.body);
                drafts.add(d);
                continue;
            }
            boolean siteRu = isSiteRu(n);
            MarketingPlatform platform = siteRu ? MarketingPlatform.SITE : platformOf(n);
            if (platform == null) {
                fileNotes.add("不认识的章节「" + s.heading + "」，已跳过");
                continue;
            }
            String part = siteRu ? PART_SITE_RU : platform.code();
            if (!seen.add(part)) {
                fileNotes.add("「" + s.heading + "」出现了多次，只用第一次");
                continue;
            }
            ContentDraft d = copyHead(head, platform.code(), part);
            if (siteRu) {
                // 英文营销文件里的俄文独立站：这一项按俄文写入
                checkLang(d, "en".equals(d.getLang()), "「## 独立站俄文」写在英文营销文件里；俄文文件只写「## 独立站」");
                d.setLang("ru");
            } else {
                checkLang(d, platform.langs().contains(d.getLang()),
                        ru ? "俄文只用于独立站，" + platform.label() + "须写在英文文件里" : platform.label() + "只能是英文（lang: en）");
            }
            if (platform == MarketingPlatform.SOCIAL) {
                parseSocial(d, s);
            } else {
                for (Section sub : sections(s.body.split("\n", -1), 0, "### ")) {
                    ContentSection section = itemSection(platform, normalize(sub.heading));
                    if (section == null) {
                        d.getNotes().add("「" + s.heading + " · " + sub.heading + "」不认识，已跳过");
                        continue;
                    }
                    put(d, section, sub.heading, sub.body);
                }
            }
            drafts.add(d);
        }
    }

    /** 「独立站俄文」「独立站（俄文）」「Website RU」等写法 */
    private static boolean isSiteRu(String n) {
        boolean site = n.startsWith("独立站") || n.startsWith("website") || n.startsWith("site");
        return site && (n.contains("俄") || n.contains("russian") || n.endsWith("ru") || n.contains("(ru)")
                || n.contains("（ru）"));
    }

    private static MarketingPlatform platformOf(String n) {
        if (n.startsWith("阿里") || n.startsWith("alibaba")) {
            return MarketingPlatform.ALIBABA;
        }
        if (n.startsWith("中国制造网") || n.startsWith("madeinchina") || n.startsWith("mic")) {
            return MarketingPlatform.MIC;
        }
        if (n.startsWith("独立站") || n.startsWith("website") || n.startsWith("site")) {
            return MarketingPlatform.SITE;
        }
        if (n.startsWith("社媒") || n.startsWith("social")) {
            return MarketingPlatform.SOCIAL;
        }
        return null;
    }

    /** 平台下的三级标题：按开头匹配，兼容常见英文写法 */
    private static ContentSection itemSection(MarketingPlatform platform, String n) {
        if (platform == MarketingPlatform.SITE) {
            if (n.startsWith("seo")) {
                return ContentSection.SEO_FIELDS;
            }
            if (n.startsWith("首屏定义块") || n.startsWith("geo") || n.startsWith("definition")) {
                return ContentSection.GEO;
            }
            if (n.startsWith("页面正文") || n.startsWith("正文") || n.startsWith("产品长描述") || n.startsWith("pagebody")) {
                return ContentSection.LONG_DESC;
            }
            if (n.startsWith("faq")) {
                return ContentSection.FAQ;
            }
            if (n.startsWith("图片说明") || n.contains("alttext")) {
                return ContentSection.ALT_TEXTS;
            }
            return null;
        }
        if (n.startsWith("标题") || n.startsWith("title")) {
            return ContentSection.TITLE;
        }
        if (n.startsWith("选用关键词") || n.startsWith("关键词") || n.startsWith("keywords")) {
            return ContentSection.KEYWORD_PICKS;
        }
        if (n.startsWith("产品属性") || n.startsWith("attributes") || n.startsWith("productattributes")) {
            return ContentSection.ATTRIBUTES;
        }
        if (n.startsWith("卖点") || n.startsWith("buyerbenefits") || n.startsWith("sellingpoints")) {
            return ContentSection.SELLING_POINTS;
        }
        if (n.startsWith("产品详情") || n.startsWith("商品详情") || n.startsWith("productdetail")) {
            return ContentSection.DETAIL;
        }
        if (n.startsWith("faq")) {
            return ContentSection.FAQ;
        }
        if (n.startsWith("询盘引导语") || n.startsWith("引导语") || n.contains("cta")) {
            return ContentSection.CTA;
        }
        return null;
    }

    /** 社媒：LinkedIn、Facebook 开头的三级标题各是一篇帖子，YouTube 开头的各是一个视频方案 */
    private static void parseSocial(ContentDraft d, Section s) {
        StringBuilder posts = new StringBuilder();
        StringBuilder videos = new StringBuilder();
        for (Section sub : sections(s.body.split("\n", -1), 0, "### ")) {
            String channel = channel(sub.heading);
            if (channel == null) {
                d.getNotes().add("「社媒 · " + sub.heading + "」不是 LinkedIn、Facebook 或 YouTube，已跳过");
                continue;
            }
            StringBuilder target = CHANNEL_YOUTUBE.equals(channel) ? videos : posts;
            target.append("### ").append(sub.heading).append('\n').append(sub.body).append('\n');
        }
        if (!posts.isEmpty()) {
            put(d, ContentSection.POSTS, "社媒帖子", posts.toString());
        }
        if (!videos.isEmpty()) {
            put(d, ContentSection.VIDEOS, "视频方案", videos.toString());
        }
    }

    private static String channel(String heading) {
        String n = normalize(heading);
        if (n.startsWith("linkedin")) {
            return CHANNEL_LINKEDIN;
        }
        if (n.startsWith("facebook")) {
            return CHANNEL_FACEBOOK;
        }
        if (n.startsWith("youtube")) {
            return CHANNEL_YOUTUBE;
        }
        return null;
    }

    // ---------------------------------------------------------------- 块

    private static void put(ContentDraft d, ContentSection section, String heading, String raw) {
        ContentDraft.Block block = parseBlock(section, raw);
        if (block.count() == 0 && block.getErrors().isEmpty()) {
            d.getNotes().add("「" + heading + "」没有可写入的内容，已跳过");
            d.getNotes().addAll(block.getNotes());
            return;
        }
        if (d.getBlocks().containsKey(section)) {
            d.getNotes().add("「" + section.label() + "」出现了多次，以最后一次为准");
        }
        d.getBlocks().put(section, block);
    }

    /** 只解析一个章节的正文（v2 预览里修改某块后重新解析用） */
    public static ContentDraft.Block parseBlock(ContentSection section, String raw) {
        String body = raw == null ? "" : raw;
        ContentDraft.Block block = new ContentDraft.Block();
        block.setSection(section);
        block.setRaw(body.strip());
        List<String> content = ContentMarkdownParser.contentLines(body);
        if (content.isEmpty()) {
            return block;
        }
        switch (section) {
            case IDENTITY -> parseIdentity(content, block);
            case SPECS -> parseSpecs(content, block);
            case UNKNOWNS -> parseUnknowns(content, block);
            case APPS -> parseApps(content, block);
            case COMPAT -> ContentMarkdownParser.parseCompat(content, block);
            case TECH_FAQ, FAQ -> parseFaq(content, block);
            case DOCS -> ContentMarkdownParser.parseDocs(content, block);
            case LOGISTICS, CUSTOMS -> ContentMarkdownParser.parseFields(content, block);
            case SOURCES -> parseSources(content, block);
            case CONFLICTS -> parseConflicts(content, block);
            case LISTING_PRICES -> parseListings(content, block);
            case CARD_PRICE -> parseCardPrice(content, block);
            case KEYWORD_LIBRARY -> parseKeywordLibrary(content, block);
            case KEYWORD_PICKS -> parsePicks(content, block);
            case TITLE -> parseTitles(content, block);
            case ATTRIBUTES, ALT_TEXTS -> parsePairs(content, block);
            case SELLING_POINTS -> ContentMarkdownParser.parsePoints(content, block);
            case SEO_FIELDS -> parseSeoFields(content, block);
            case POSTS, VIDEOS -> parsePosts(body, block);
            default -> ContentMarkdownParser.parseText(content, body, block);
        }
        return block;
    }

    /** 产品识别：「项目 | 内容」，取中文名称、英文名称、系列；品类等其他项不导入 */
    private static void parseIdentity(List<String> content, ContentDraft.Block block) {
        for (ContentMarkdownParser.TableRow row : ContentMarkdownParser.table(content, block)) {
            if (row.cells().size() < 2) {
                continue;
            }
            String item = normalize(row.cells().get(0));
            String value = row.cells().get(1);
            if (isBlankValue(value)) {
                continue;
            }
            String key = item.startsWith("中文名称") ? NAME_ZH
                    : item.startsWith("英文名称") ? NAME_EN
                    : item.startsWith("系列") ? SERIES : null;
            if (key == null) {
                continue;
            }
            ContentMarkdownParser.check(block, row.lineNo(), row.cells().get(0), value, key.equals(SERIES) ? 64 : 255);
            block.getFields().put(key, value);
        }
    }

    /** 规格参数：「事实编号 | 项目 | 数值及单位 | 条件/范围 | 证据状态 | 来源」；数值待确认的行不写入 */
    private static void parseSpecs(List<String> content, ContentDraft.Block block) {
        List<String> pending = new ArrayList<>();
        for (ContentMarkdownParser.TableRow row : ContentMarkdownParser.table(content, block)) {
            List<String> c = row.cells();
            if (c.size() < 3 || c.get(1).isEmpty()) {
                block.getErrors().add("第 " + row.lineNo() + " 行须为「事实编号 | 项目 | 数值及单位 | 条件/范围 | 证据状态 | 来源」");
                continue;
            }
            String label = c.get(1);
            String value = c.get(2);
            if (isBlankValue(value)) {
                pending.add(label);
                continue;
            }
            String condition = cell(c, 3);
            String evidence = cell(c, 4);
            String source = cell(c, 5);
            ContentMarkdownParser.check(block, row.lineNo(), "项目", label, 64);
            ContentMarkdownParser.check(block, row.lineNo(), "数值及单位", value, 256);
            ContentMarkdownParser.check(block, row.lineNo(), "条件/范围", condition, 256);
            ContentMarkdownParser.check(block, row.lineNo(), "证据状态", evidence, 64);
            block.getSpecs().add(new ContentDraft.SpecRow(label, value, "", condition, evidence, source));
        }
        if (!pending.isEmpty()) {
            block.getNotes().add("数值待确认，未写入：" + String.join("、", pending));
        }
    }

    private static void parseUnknowns(List<String> content, ContentDraft.Block block) {
        for (ContentMarkdownParser.TableRow row : ContentMarkdownParser.table(content, block)) {
            List<String> c = row.cells();
            if (c.isEmpty() || c.get(0).isEmpty()) {
                continue;
            }
            ContentMarkdownParser.check(block, row.lineNo(), "项目", c.get(0), 256);
            ContentMarkdownParser.check(block, row.lineNo(), "当前状态", cell(c, 1), 500);
            ContentMarkdownParser.check(block, row.lineNo(), "影响", cell(c, 2), 500);
            block.getNotesRows().add(new ContentDraft.NoteRow(1, c.get(0), cell(c, 1), cell(c, 2)));
        }
    }

    /** 冲突与待确认：编号列表，每条以「**要点**」开头；续行并入上一条 */
    private static void parseConflicts(List<String> content, ContentDraft.Block block) {
        String item = null;
        StringBuilder rest = null;
        for (String line : content) {
            String t = line.strip();
            Matcher bold = BOLD_ITEM.matcher(t);
            Matcher numbered = NUMBERED.matcher(t);
            if (bold.matches() || numbered.matches() || t.startsWith("- ") || t.startsWith("* ")) {
                addConflict(block, item, rest);
                if (bold.matches()) {
                    item = bold.group(1).strip();
                    rest = new StringBuilder(bold.group(2).strip());
                } else {
                    String whole = numbered.matches() ? numbered.group(1).strip() : t.substring(2).strip();
                    item = whole.length() > 60 ? whole.substring(0, 60) + "…" : whole;
                    rest = new StringBuilder(whole.length() > 60 ? whole : "");
                }
            } else if (rest != null) {
                rest.append(rest.isEmpty() ? "" : " ").append(t);
            } else {
                item = t.length() > 60 ? t.substring(0, 60) + "…" : t;
                rest = new StringBuilder(t.length() > 60 ? t : "");
            }
        }
        addConflict(block, item, rest);
    }

    private static void addConflict(ContentDraft.Block block, String item, StringBuilder rest) {
        if (item == null || item.isEmpty()) {
            return;
        }
        String impact = rest == null ? "" : rest.toString().strip();
        if (item.length() > 256) {
            item = item.substring(0, 256);
        }
        if (impact.length() > 500) {
            impact = impact.substring(0, 499) + "…";
        }
        block.getNotesRows().add(new ContentDraft.NoteRow(2, item, "", impact));
    }

    /** 应用需求：「场景 | 买家需求 | 产品作用 | 选型前提 | 证据性质/来源」→ 标题=场景，描述=产品作用；选型前提 */
    private static void parseApps(List<String> content, ContentDraft.Block block) {
        for (ContentMarkdownParser.TableRow row : ContentMarkdownParser.table(content, block)) {
            List<String> c = row.cells();
            if (c.isEmpty() || c.get(0).isEmpty()) {
                block.getErrors().add("第 " + row.lineNo() + " 行缺少场景");
                continue;
            }
            String role = cell(c, 2).isEmpty() ? cell(c, 1) : cell(c, 2);
            String premise = cell(c, 3);
            String desc = premise.isEmpty() ? role : (role.isEmpty() ? "" : role + "；") + "选型前提：" + premise;
            ContentMarkdownParser.check(block, row.lineNo(), "场景", c.get(0), 64);
            ContentMarkdownParser.check(block, row.lineNo(), "描述", desc, 500);
            block.getApps().add(new ContentDraft.AppRow("", c.get(0), desc));
        }
    }

    /** 来源索引：「编号 | 发布者及范围 | 日期 | 链接 | 支持事实」；链接可以是 Markdown 链接或地址 */
    private static void parseSources(List<String> content, ContentDraft.Block block) {
        for (ContentMarkdownParser.TableRow row : ContentMarkdownParser.table(content, block)) {
            List<String> c = row.cells();
            if (c.size() < 2 || c.get(0).isEmpty()) {
                continue;
            }
            String link = cell(c, 3);
            Matcher m = LINK.matcher(link);
            if (m.find()) {
                link = m.group(2).strip();
            }
            block.getSources().add(new ContentDraft.SourceRow(c.get(0), c.get(1), link));
        }
    }

    /** 关键词库：「优先级 | 分类 | Keyword | 搜索意图 | 事实依据」，库内顺序按表格；同词只留第一个 */
    private static void parseKeywordLibrary(List<String> content, ContentDraft.Block block) {
        Set<String> seen = new HashSet<>();
        List<String> duplicates = new ArrayList<>();
        for (ContentMarkdownParser.TableRow row : ContentMarkdownParser.table(content, block)) {
            List<String> c = row.cells();
            String keyword = cell(c, 2);
            if (keyword.isEmpty()) {
                block.getErrors().add("第 " + row.lineNo() + " 行缺少关键词（第三列 Keyword）");
                continue;
            }
            int tier = priority(cell(c, 0));
            if (tier == 0) {
                block.getErrors().add("第 " + row.lineNo() + " 行优先级须为 P1、P2、P3（或核心、主要、长尾）");
                continue;
            }
            ContentMarkdownParser.check(block, row.lineNo(), "关键词", keyword, 128);
            ContentMarkdownParser.check(block, row.lineNo(), "分类", cell(c, 1), 64);
            ContentMarkdownParser.check(block, row.lineNo(), "搜索意图", cell(c, 3), 128);
            ContentMarkdownParser.check(block, row.lineNo(), "事实依据", cell(c, 4), 128);
            if (!seen.add(keywordKey(keyword))) {
                duplicates.add(keyword);
                continue;
            }
            block.getKeywords().add(new ContentDraft.KeywordRow(keyword, tier, cell(c, 1), cell(c, 3), cell(c, 4)));
        }
        if (!duplicates.isEmpty()) {
            block.getNotes().add("重复的 " + duplicates.size() + " 个关键词只留第一个：" + String.join("、", duplicates));
        }
        if (block.getKeywords().size() > ContentConstants.MAX_KEYWORDS) {
            int over = block.getKeywords().size() - ContentConstants.MAX_KEYWORDS;
            block.getKeywords().subList(ContentConstants.MAX_KEYWORDS, block.getKeywords().size()).clear();
            block.getNotes().add("关键词超过 " + ContentConstants.MAX_KEYWORDS + " 个，只保留前 "
                    + ContentConstants.MAX_KEYWORDS + " 个，忽略 " + over + " 个");
        }
    }

    static int priority(String raw) {
        String n = normalize(raw);
        Matcher m = PRIORITY.matcher(n);
        if (m.matches()) {
            return Integer.parseInt(m.group(1));
        }
        return ContentMarkdownParser.tier(raw);
    }

    /** 选用关键词：每行一个，去掉列表符号与加粗；同词只留一个 */
    private static void parsePicks(List<String> content, ContentDraft.Block block) {
        Set<String> seen = new HashSet<>();
        for (String line : content) {
            String t = line.strip().replaceFirst("^(?:[-*•]\\s*|\\d+[.、)）]\\s*)", "").replace("**", "").strip();
            if (t.startsWith("|")) {
                List<String> cells = new ArrayList<>(List.of(t.substring(1).split("\\|")));
                t = cells.isEmpty() ? "" : cells.get(0).strip();
                if (t.matches(":?-{2,}:?") || normalize(t).equals("keyword")) {
                    continue;
                }
            }
            if (t.isEmpty() || !seen.add(keywordKey(t))) {
                continue;
            }
            if (t.length() > 128) {
                block.getErrors().add("关键词「" + t.substring(0, 20) + "…」超过 128 字");
                continue;
            }
            block.getPicks().add(t);
        }
    }

    /** 标题：「版本 | English title | 字符数 | 角度」，第一行为推荐；也接受只写一行标题 */
    private static void parseTitles(List<String> content, ContentDraft.Block block) {
        if (!content.get(0).strip().startsWith("|")) {
            ContentMarkdownParser.parseText(content, String.join("\n", content), block);
            if (block.getText() != null && !block.getText().isEmpty()) {
                block.getTitles().add(new ContentDraft.TitleRow(block.getText(), ""));
            }
            return;
        }
        for (ContentMarkdownParser.TableRow row : ContentMarkdownParser.table(content, block)) {
            List<String> c = row.cells();
            String title = c.size() > 1 ? c.get(1) : cell(c, 0);
            if (title.isEmpty()) {
                continue;
            }
            ContentMarkdownParser.check(block, row.lineNo(), "标题", title, ContentSection.TITLE.maxLength());
            block.getTitles().add(new ContentDraft.TitleRow(title, cell(c, 3)));
        }
        if (!block.getTitles().isEmpty()) {
            block.setText(block.getTitles().get(0).title());
        }
    }

    /** 产品属性、图片说明文字：两列；第一列是 Markdown 链接时取链接文字 */
    private static void parsePairs(List<String> content, ContentDraft.Block block) {
        for (ContentMarkdownParser.TableRow row : ContentMarkdownParser.table(content, block)) {
            List<String> c = row.cells();
            if (c.size() < 2 || c.get(0).isEmpty() || isBlankValue(c.get(1))) {
                continue;
            }
            String name = c.get(0);
            Matcher m = LINK.matcher(name);
            if (m.find() && !m.group(1).isBlank()) {
                name = m.group(1).strip();
            }
            ContentMarkdownParser.check(block, row.lineNo(), "名称", name, 128);
            ContentMarkdownParser.check(block, row.lineNo(), "内容", c.get(1), 500);
            block.getPairs().add(new ContentDraft.PairRow(name, c.get(1)));
        }
    }

    /** SEO 字段：「Field | English copy」，Field 为 SEO Title、Meta Description、H1、URL Slug */
    private static void parseSeoFields(List<String> content, ContentDraft.Block block) {
        for (ContentMarkdownParser.TableRow row : ContentMarkdownParser.table(content, block)) {
            List<String> c = row.cells();
            if (c.size() < 2 || isBlankValue(c.get(1))) {
                continue;
            }
            String n = normalize(c.get(0));
            String value = c.get(1);
            if (n.contains("seotitle") || n.equals("title") || n.startsWith("seo标题")) {
                ContentMarkdownParser.check(block, row.lineNo(), "SEO Title", value, ContentSection.SEO_TITLE.maxLength());
                block.getFields().put(SEO_TITLE, value);
            } else if (n.contains("description") || n.startsWith("seo描述")) {
                ContentMarkdownParser.check(block, row.lineNo(), "Meta Description", value,
                        ContentSection.SEO_DESC.maxLength());
                block.getFields().put(META_DESCRIPTION, value);
            } else if (n.startsWith("h1")) {
                ContentMarkdownParser.check(block, row.lineNo(), "H1", value, 255);
                block.getFields().put(H1, value);
            } else if (n.contains("slug") || n.contains("url")) {
                ContentMarkdownParser.check(block, row.lineNo(), "URL Slug", value, 255);
                block.getFields().put(URL_SLUG, value);
            }
        }
    }

    /** 帖子、视频方案：按「### 渠道…」切分，正文原样保存 */
    private static void parsePosts(String raw, ContentDraft.Block block) {
        for (Section s : sections(HTML_COMMENT.matcher(raw).replaceAll("").split("\n", -1), 0, "### ")) {
            String channel = channel(s.heading);
            String text = s.body.strip();
            if (channel == null || text.isEmpty()) {
                continue;
            }
            int bar = Math.max(s.heading.indexOf('｜'), s.heading.indexOf('|'));
            String label = bar >= 0 ? s.heading.substring(bar + 1).strip() : s.heading.strip();
            int max = SOCIAL_LIMITS.get(channel);
            if (text.length() > max) {
                block.getErrors().add("「" + s.heading + "」超过 " + max + " 字");
            }
            block.getPosts().add(new ContentDraft.PostRow(channel, label.length() > 128 ? label.substring(0, 128) : label,
                    text));
        }
    }

    /** 技术问答、FAQ：支持「Q: / A:」成对，或「1. **问题** 答案」 */
    /** 平台参考价：「价格类别 | 平台/网站 | 成色 | 参考价格（USD）| 来源原价 | 来源链接」 */
    private static void parseListings(List<String> content, ContentDraft.Block block) {
        for (ContentMarkdownParser.TableRow row : ContentMarkdownParser.table(content, block)) {
            List<String> c = row.cells();
            Matcher money = MONEY.matcher(cell(c, 3));
            if (c.size() < 4 || !money.find()) {
                block.getErrors().add("第 " + row.lineNo() + " 行须为「价格类别 | 平台/网站 | 成色 | 参考价格（USD）| 来源原价 | 来源链接」，参考价格要有数字");
                continue;
            }
            BigDecimal usd = new BigDecimal(money.group().replace(",", "")).setScale(2, RoundingMode.HALF_UP);
            if (usd.signum() <= 0) {
                block.getErrors().add("第 " + row.lineNo() + " 行参考价格须大于 0");
                continue;
            }
            String link = cell(c, 5);
            String title = "";
            String url = "";
            Matcher m = LINK.matcher(link);
            if (m.find()) {
                title = m.group(1).strip();
                url = m.group(2).strip();
            } else if (link.matches("https?://\\S+")) {
                url = link;
            }
            ContentMarkdownParser.check(block, row.lineNo(), "价格类别", cell(c, 0), 64);
            ContentMarkdownParser.check(block, row.lineNo(), "平台/网站", cell(c, 1), 64);
            ContentMarkdownParser.check(block, row.lineNo(), "成色", cell(c, 2), 32);
            ContentMarkdownParser.check(block, row.lineNo(), "来源原价", cell(c, 4), 64);
            ContentMarkdownParser.check(block, row.lineNo(), "链接", url, 500);
            block.getListings().add(new ContentDraft.ListingRow(cell(c, 0), cell(c, 1), cell(c, 2), usd, cell(c, 4),
                    title.length() > 255 ? title.substring(0, 255) : title, url));
        }
    }

    /**
     * 商品平台参考价：「字段 | 内容」，字段为币种、参考价（原币）、汇率（原币 → 人民币）、价格来源、取价日期。
     * 校验与商品卡片手工保存一致（ProductReferencePriceServiceImpl）：币种 ISO 4217；参考价大于 0、HALF_UP 保留 2 位；
     * 汇率大于 0、HALF_UP 保留 6 位；来源最长 128 字；日期 YYYY-MM-DD。未知值留空，写入时不覆盖已有值。
     */
    private static void parseCardPrice(List<String> content, ContentDraft.Block block) {
        String currency = null;
        BigDecimal original = null;
        BigDecimal rate = null;
        String source = null;
        LocalDate date = null;
        boolean currencyGiven = false;
        boolean priceGiven = false;
        for (ContentMarkdownParser.TableRow row : ContentMarkdownParser.table(content, block)) {
            List<String> c = row.cells();
            String key = c.isEmpty() ? "" : normalize(c.get(0)).replaceAll("[（）()→>\\-]", "");
            if (key.isEmpty() || "字段".equals(key)) {
                continue;
            }
            String raw = c.size() > 1 ? c.get(1).strip() : "";
            boolean unknown = isUnknownValue(raw);
            String at = "第 " + row.lineNo() + " 行";
            if (key.startsWith("币种") || key.startsWith("currency")) {
                if (!unknown) {
                    currencyGiven = true;
                    if (!ISO_CURRENCIES.contains(raw)) {
                        block.getErrors().add(at + "币种须为三位大写 ISO 4217 代码，如 USD、CNY");
                    } else {
                        currency = raw;
                    }
                }
            } else if (key.startsWith("参考价") || key.startsWith("原币参考价") || key.startsWith("price")) {
                if (!unknown) {
                    priceGiven = true;
                    original = decimal(raw, at + "参考价（原币）", 2, AMOUNT_LIMIT, block);
                }
            } else if (key.startsWith("汇率") || key.startsWith("exchangerate")) {
                if (!unknown) {
                    rate = decimal(raw, at + "汇率", 6, RATE_LIMIT, block);
                }
            } else if (key.startsWith("价格来源") || key.startsWith("来源") || key.startsWith("source")) {
                if (!unknown) {
                    ContentMarkdownParser.check(block, row.lineNo(), "价格来源", raw, 128);
                    source = raw.length() > 128 ? raw.substring(0, 128) : raw;
                }
            } else if (key.startsWith("取价日期") || key.startsWith("日期") || key.startsWith("date")) {
                if (!unknown) {
                    try {
                        date = LocalDate.parse(raw);
                    } catch (DateTimeParseException e) {
                        block.getErrors().add(at + "取价日期须为 YYYY-MM-DD，如 2026-10-19");
                    }
                }
            } else {
                block.getNotes().add(at + "字段「" + c.get(0) + "」不认识，已跳过");
            }
        }
        if (!currencyGiven && !priceGiven) {
            block.getNotes().add("币种和参考价未知，不写入商品平台参考价");
            return;
        }
        if (currencyGiven != priceGiven) {
            block.getErrors().add("币种和参考价（原币）要同时提供；只知道其中一个时不能写入商品平台参考价");
            return;
        }
        if (currency == null || original == null) {
            return;
        }
        if ("CNY".equals(currency)) {
            // 人民币按 1 处理，表里写的汇率不用
            rate = BigDecimal.ONE.setScale(6);
        }
        block.setCardPrice(new ContentDraft.CardPriceRow(currency, original, rate, source, date));
    }

    /** 大于 0 的数（可带千分位逗号），HALF_UP 保留 scale 位后仍大于 0 且不超过上限；不合规时记错误并返回空 */
    private static BigDecimal decimal(String raw, String label, int scale, BigDecimal limit, ContentDraft.Block block) {
        String t = raw.replace(",", "");
        if (!t.matches("\\d+(?:\\.\\d+)?")) {
            block.getErrors().add(label + "须为大于 0 的数字，当前为「" + raw + "」");
            return null;
        }
        BigDecimal v = new BigDecimal(t).setScale(scale, RoundingMode.HALF_UP);
        if (v.signum() <= 0) {
            block.getErrors().add(label + "须大于 0");
            return null;
        }
        if (v.compareTo(limit) >= 0) {
            block.getErrors().add(label + "数值过大");
            return null;
        }
        return v;
    }

    /** 未知值：空、破折号、含「[待确认]」，以及「未知」「unknown」 */
    static boolean isUnknownValue(String v) {
        if (isBlankValue(v)) {
            return true;
        }
        String t = v.strip().toLowerCase(Locale.ROOT);
        return t.equals("未知") || t.equals("unknown") || t.equals("n/a");
    }

    private static void parseFaq(List<String> content, ContentDraft.Block block) {
        if (content.stream().anyMatch(l -> BOLD_NUMBERED.matcher(l.strip()).matches())) {
            parseBoldFaq(content, block, BOLD_NUMBERED);
            return;
        }
        boolean bold = content.stream().anyMatch(l -> BOLD_ITEM.matcher(l.strip()).matches());
        if (!bold) {
            ContentMarkdownParser.parseFaq(content, block);
            return;
        }
        parseBoldFaq(content, block, BOLD_ITEM);
    }

    /** 加粗问题后跟答案（同一行或下一段） */
    private static void parseBoldFaq(List<String> content, ContentDraft.Block block, Pattern pattern) {
        String question = null;
        StringBuilder answer = null;
        int lineNo = 0;
        int qLine = 0;
        for (String line : content) {
            lineNo++;
            String t = line.strip();
            if (t.isEmpty()) {
                continue;
            }
            Matcher m = pattern.matcher(t);
            if (m.matches()) {
                addFaq(block, question, answer, qLine);
                question = m.group(1).strip();
                answer = new StringBuilder(m.group(2).strip());
                qLine = lineNo;
            } else if (answer != null) {
                answer.append(answer.isEmpty() ? "" : " ").append(t);
            } else {
                block.getErrors().add("第 " + lineNo + " 行不在任何问题之下");
            }
        }
        addFaq(block, question, answer, qLine);
    }

    private static void addFaq(ContentDraft.Block block, String question, StringBuilder answer, int lineNo) {
        if (question == null) {
            return;
        }
        if (answer == null || answer.isEmpty()) {
            block.getErrors().add("第 " + lineNo + " 行的问题缺少答案");
            return;
        }
        ContentMarkdownParser.check(block, lineNo, "问题", question, 256);
        ContentMarkdownParser.check(block, lineNo, "答案", answer.toString(), 5000);
        block.getFaqs().add(new ContentDraft.FaqRow(question, answer.toString()));
    }

    // ---------------------------------------------------------------- 来源名称回填

    /**
     * 规格的来源编号（S02、S03）按同一文件的来源索引换成「S02 名称；S03 名称」。
     * 编号都在来源索引里时才替换，否则保留原文；在预览、修改之后调用。
     */
    public static void resolveSources(ContentDraft draft) {
        ContentDraft.Block specs = draft.getBlocks().get(ContentSection.SPECS);
        ContentDraft.Block sources = draft.getBlocks().get(ContentSection.SOURCES);
        if (specs == null || sources == null) {
            return;
        }
        Map<String, String> names = new HashMap<>();
        sources.getSources().forEach(s -> names.put(s.code().strip().toUpperCase(Locale.ROOT), s.name()));
        List<ContentDraft.SpecRow> rows = specs.getSpecs();
        for (int i = 0; i < rows.size(); i++) {
            String note = rows.get(i).sourceNote();
            Matcher m = SOURCE_CODE.matcher(note == null ? "" : note);
            Map<String, String> found = new LinkedHashMap<>();
            boolean all = true;
            while (m.find()) {
                String name = names.get(m.group());
                if (name == null) {
                    all = false;
                    break;
                }
                found.put(m.group(), name);
            }
            if (all && !found.isEmpty()) {
                List<String> parts = new ArrayList<>();
                found.forEach((code, name) -> parts.add(code + " " + name));
                String resolved = String.join("；", parts);
                rows.set(i, rows.get(i).withSourceNote(resolved.length() > 256 ? resolved.substring(0, 255) + "…" : resolved));
            }
        }
    }

    // ---------------------------------------------------------------- 工具

    private record Section(String heading, String body) {
    }

    /** 按标题前缀（"## " 或 "### "）切分；第一个标题之前的内容丢弃 */
    private static List<Section> sections(String[] lines, int start, String prefix) {
        List<Section> result = new ArrayList<>();
        String heading = null;
        StringBuilder body = new StringBuilder();
        boolean fence = false;
        for (int i = start; i < lines.length; i++) {
            String line = lines[i];
            if (line.strip().startsWith("```")) {
                fence = !fence;
            }
            if (!fence && line.startsWith(prefix)) {
                if (heading != null) {
                    result.add(new Section(heading, body.toString()));
                }
                heading = line.substring(prefix.length()).strip();
                body.setLength(0);
                continue;
            }
            if (heading != null) {
                body.append(line).append('\n');
            }
        }
        if (heading != null) {
            result.add(new Section(heading, body.toString()));
        }
        return result;
    }

    private static String cell(List<String> cells, int i) {
        return i < cells.size() && !isBlankValue(cells.get(i)) ? cells.get(i) : "";
    }

    /** 空、破折号、含「[待确认]」的值视为没有 */
    static boolean isBlankValue(String v) {
        if (v == null) {
            return true;
        }
        String t = v.strip();
        return t.isEmpty() || t.matches("[—–\\-]+") || t.contains("[待确认]");
    }

    static String keywordKey(String keyword) {
        return keyword == null ? "" : keyword.strip().toLowerCase(Locale.ROOT);
    }

    private static String normalize(String heading) {
        return ContentMarkdownParser.normalize(heading).replace("*", "");
    }
}
