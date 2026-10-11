package com.zhul.erp.modules.product.content.support;

import java.util.List;

/**
 * 内容包章节：标题按开头匹配（去空白、忽略大小写）。
 * 每个章节属于资料包（facts，写共享商品库）或某个营销平台的营销包（写本公司该平台）。
 */
public enum ContentSection {
    SUMMARY("一句话规格摘要", Kind.TEXT, 300, List.of("facts"), List.of("一句话规格摘要", "规格摘要", "specsummary", "summary")),
    GEO("首屏定义块", Kind.TEXT, 1000, List.of("site"), List.of("首屏定义块", "geo", "definition")),
    SEO_TITLE("SEO 标题", Kind.TEXT, 120, List.of("site"), List.of("seo标题", "seotitle")),
    SEO_DESC("SEO 描述", Kind.TEXT, 320, List.of("site"), List.of("seo描述", "metadescription", "seodescription")),
    LONG_DESC("产品长描述", Kind.LINES, 5000, List.of("site"),
            List.of("产品长描述", "长描述", "longdescription", "productdescription")),
    SPECS("规格参数表", Kind.LIST, 0, List.of("facts"), List.of("规格参数表", "规格参数", "specifications", "specs")),
    APPS("应用场景", Kind.LIST, 0, List.of("facts"), List.of("应用场景", "applications")),
    COMPAT("兼容替代型号", Kind.LIST, 0, List.of("facts"), List.of("兼容替代型号", "兼容型号", "compatible", "compatibility")),
    FAQ("FAQ", Kind.LIST, 0, List.of("site"), List.of("faq", "营销问答")),
    DOCS("技术资料", Kind.LIST, 0, List.of("facts"), List.of("技术资料", "documents", "datasheet", "downloads")),
    TECH_FAQ("技术问答", Kind.LIST, 0, List.of("facts"), List.of("技术问答", "技术faq", "technicalfaq")),
    SERIES("系列", Kind.TEXT, 64, List.of("facts"), List.of("系列", "series")),
    LOGISTICS("物流信息", Kind.LIST, 0, List.of("facts"), List.of("物流信息", "物流", "logistics")),
    CUSTOMS("海关信息", Kind.LIST, 0, List.of("facts"), List.of("海关信息", "海关", "customs")),
    TITLE("标题", Kind.TEXT, 128, List.of("alibaba", "mic"), List.of("标题", "producttitle", "title")),
    KEYWORDS("关键词库", Kind.LIST, 0, List.of("alibaba", "mic"), List.of("关键词", "keywords", "keyword")),
    SELLING_POINTS("卖点", Kind.LIST, 0, List.of("alibaba"), List.of("卖点", "商品卖点", "sellingpoints", "highlights")),
    DETAIL("商品详情", Kind.RAW, 20000, List.of("mic"), List.of("商品详情", "产品详情", "详情", "productdetail", "detail")),
    LINKEDIN("LinkedIn", Kind.RAW, 3000, List.of("social"), List.of("linkedin")),
    FACEBOOK("Facebook", Kind.RAW, 5000, List.of("social"), List.of("facebook")),
    YOUTUBE("YouTube 视频描述", Kind.RAW, 5000, List.of("social"), List.of("youtube"));

    /** 章节归属：资料包，或营销平台编码（见 MarketingPlatform） */
    public static final String FACTS = "facts";
    public static final String ALIBABA = "alibaba";
    public static final String MIC = "mic";
    public static final String SITE = "site";
    public static final String SOCIAL = "social";

    /** 以后才处理的章节，解析时忽略 */
    public static final List<String> LATER = List.of("价格", "price", "销售话术", "图片", "视频", "media");

    /** TEXT 合成一段纯文本；LINES 保留换行、去掉空行；RAW 保留原文（含空行、表情符号）；LIST 解析为条目 */
    public enum Kind { TEXT, LINES, RAW, LIST }

    private final String label;
    private final Kind kind;
    private final int maxLength;
    private final List<String> scopes;
    private final List<String> aliases;

    ContentSection(String label, Kind kind, int maxLength, List<String> scopes, List<String> aliases) {
        this.label = label;
        this.kind = kind;
        this.maxLength = maxLength;
        this.scopes = scopes;
        this.aliases = aliases;
    }

    public String label() {
        return label;
    }

    /** true 属于资料包、写入共享商品库，false 属于营销包、写入本公司 */
    public boolean shared() {
        return scopes.contains(FACTS);
    }

    /** 属于资料包（facts）或该平台的营销包 */
    public boolean in(String scope) {
        return scopes.contains(scope);
    }

    public Kind kind() {
        return kind;
    }

    /** 文本块的长度上限；列表块为 0 */
    public int maxLength() {
        return maxLength;
    }

    public boolean isText() {
        return kind != Kind.LIST;
    }

    /** scope 为空时在所有章节里找，否则只在该包类型或平台的章节里找 */
    static ContentSection match(String normalizedHeading, String scope) {
        for (ContentSection s : values()) {
            if (scope != null && !s.in(scope)) {
                continue;
            }
            for (String alias : s.aliases) {
                if (normalizedHeading.startsWith(alias)) {
                    return s;
                }
            }
        }
        return null;
    }
}
