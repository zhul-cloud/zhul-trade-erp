package com.zhul.erp.modules.product.content.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 商品详情里的内容：任务阶段、资料状态（共享）、本公司启用平台的营销内容与写入状态 */
@Data
public class ProductContentVO {
    /** 任务阶段：1-资料待补、2-资料待核、3-营销待生成、4-已完成 */
    private Integer status;
    /** 资料（所有公司共享）：最近导入、核对 */
    private LocalDateTime factImportedAt;
    private String factImportedBy;
    private LocalDateTime factVerifiedAt;
    private String factVerifiedBy;
    /** 本公司启用的平台编码，按固定顺序 */
    private List<String> platforms = new ArrayList<>();
    /** 本公司各平台写入时间：独立站英文、俄文，阿里国际站、中国制造网、社媒 */
    private LocalDateTime enAt;
    private LocalDateTime ruAt;
    private LocalDateTime alibabaAt;
    private LocalDateTime micAt;
    private LocalDateTime socialAt;
    /** 本公司标了不发布的平台：平台编码 → 标记 */
    private Map<String, MarketingSkipVO> skips = new HashMap<>();
    /** 独立站：en / ru → 本公司该语言营销内容 */
    private Map<String, LangContent> langs = new LinkedHashMap<>();
    /** 资料待确认项（所有公司共享）：关键未知字段、冲突与待确认 */
    private List<FactNote> factNotes = new ArrayList<>();
    /** 本公司这个型号的关键词总库：按档位、库内顺序，标出被哪些启用平台选用 */
    private List<LibraryKeyword> keywordLibrary = new ArrayList<>();
    /** 阿里国际站、中国制造网、社媒：平台编码 → 本公司营销内容（只含启用且写过的平台） */
    private Map<String, PlatformContent> platformContents = new LinkedHashMap<>();

    @Data
    public static class LangContent {
        private String seoTitle;
        private String metaDescription;
        private String longDescription;
        private String geoAnswer;
        private String h1;
        private String urlSlug;
        private List<AltText> altTexts = new ArrayList<>();
        private List<Faq> faqs = new ArrayList<>();
        private String fileName;
        private String confirmedBy;
        private LocalDateTime confirmedAt;
    }

    @Data
    public static class PlatformContent {
        private String title;
        private List<TitleAlternate> titleAlternates = new ArrayList<>();
        /** 该平台选用的关键词，按选用顺序 */
        private List<Keyword> keywords = new ArrayList<>();
        private List<Attribute> attributes = new ArrayList<>();
        private List<String> sellingPoints = new ArrayList<>();
        private String detail;
        private List<Faq> faqs = new ArrayList<>();
        private String cta;
        /** 社媒帖子（v2）；为空时看下面旧的三列 */
        private List<Post> posts = new ArrayList<>();
        private List<Video> videos = new ArrayList<>();
        private String linkedinPost;
        private String facebookPost;
        private String youtubeDescription;
        private String fileName;
        private String confirmedBy;
        private LocalDateTime confirmedAt;
    }

    @Data
    public static class LibraryKeyword {
        private String keyword;
        /** 1-核心、2-主要、3-长尾 */
        private Integer tier;
        private String category;
        private String intent;
        private String basis;
        /** 选用这个词的平台编码，按平台固定顺序 */
        private List<String> platforms = new ArrayList<>();
    }

    @Data
    public static class Keyword {
        private String keyword;
        private Integer tier;
    }

    @Data
    public static class FactNote {
        /** 1-关键未知字段、2-冲突与待确认 */
        private Integer kind;
        private String item;
        private String statusText;
        private String impact;
    }

    @Data
    public static class TitleAlternate {
        private String title;
        private String angle;
    }

    @Data
    public static class Attribute {
        private String name;
        private String value;
    }

    @Data
    public static class AltText {
        private String image;
        private String alt;
    }

    @Data
    public static class Post {
        /** LinkedIn、Facebook */
        private String channel;
        private String label;
        private String text;
    }

    @Data
    public static class Video {
        private String label;
        private String markdown;
    }

    @Data
    public static class Faq {
        private String question;
        private String answer;
    }
}
