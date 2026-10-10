package com.zhul.erp.modules.product.content.support;

import java.util.List;

/** 内容包章节：标题按开头匹配（去空白、忽略大小写），写入位置分共享商品库与本公司 */
public enum ContentSection {
    SUMMARY("一句话规格摘要", true, 300, List.of("一句话规格摘要", "规格摘要", "specsummary", "summary")),
    GEO("首屏定义块", false, 1000, List.of("首屏定义块", "geo", "definition")),
    SEO_TITLE("SEO 标题", false, 120, List.of("seo标题", "seotitle")),
    SEO_DESC("SEO 描述", false, 320, List.of("seo描述", "metadescription", "seodescription")),
    LONG_DESC("产品长描述", false, 5000, List.of("产品长描述", "长描述", "longdescription", "productdescription")),
    SPECS("规格参数表", true, 0, List.of("规格参数表", "规格参数", "specifications", "specs")),
    APPS("应用场景", true, 0, List.of("应用场景", "applications")),
    COMPAT("兼容替代型号", true, 0, List.of("兼容替代型号", "兼容型号", "compatible", "compatibility")),
    FAQ("FAQ", false, 0, List.of("faq")),
    DOCS("技术资料", true, 0, List.of("技术资料", "documents", "datasheet", "downloads"));

    /** 第三期才处理的章节，解析时忽略 */
    public static final List<String> LATER = List.of("价格", "price", "社媒", "social", "销售话术", "图片", "视频", "media");

    private final String label;
    private final boolean shared;
    private final int maxLength;
    private final List<String> aliases;

    ContentSection(String label, boolean shared, int maxLength, List<String> aliases) {
        this.label = label;
        this.shared = shared;
        this.maxLength = maxLength;
        this.aliases = aliases;
    }

    public String label() {
        return label;
    }

    /** true 写入共享商品库，false 写入本公司 */
    public boolean shared() {
        return shared;
    }

    /** 文本块的长度上限；列表块为 0 */
    public int maxLength() {
        return maxLength;
    }

    public boolean isText() {
        return maxLength > 0;
    }

    static ContentSection match(String normalizedHeading) {
        for (ContentSection s : values()) {
            for (String alias : s.aliases) {
                if (normalizedHeading.startsWith(alias)) {
                    return s;
                }
            }
        }
        return null;
    }
}
