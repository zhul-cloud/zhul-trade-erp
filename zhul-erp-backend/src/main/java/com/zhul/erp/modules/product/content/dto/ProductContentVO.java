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
    /** 阿里国际站、中国制造网、社媒：平台编码 → 本公司营销内容（只含启用且写过的平台） */
    private Map<String, PlatformContent> platformContents = new LinkedHashMap<>();

    @Data
    public static class LangContent {
        private String seoTitle;
        private String metaDescription;
        private String longDescription;
        private String geoAnswer;
        private List<Faq> faqs = new ArrayList<>();
        private String fileName;
        private String confirmedBy;
        private LocalDateTime confirmedAt;
    }

    @Data
    public static class PlatformContent {
        private String title;
        /** 按档位（1-核心、2-主要、3-长尾）、档内顺序排好 */
        private List<Keyword> keywords = new ArrayList<>();
        private List<String> sellingPoints = new ArrayList<>();
        private String detail;
        private String linkedinPost;
        private String facebookPost;
        private String youtubeDescription;
        private String fileName;
        private String confirmedBy;
        private LocalDateTime confirmedAt;
    }

    @Data
    public static class Keyword {
        private String keyword;
        private Integer tier;
    }

    @Data
    public static class Faq {
        private String question;
        private String answer;
    }
}
