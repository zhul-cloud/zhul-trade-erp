package com.zhul.erp.modules.product.content.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 商品详情里的内容：本公司 SEO/GEO 与 FAQ、各语言规格摘要、本公司内容任务进度 */
@Data
public class ProductContentVO {
    private Integer status;
    private LocalDateTime zhAt;
    private LocalDateTime enAt;
    private LocalDateTime ruAt;
    /** zh / en / ru → 该语言内容 */
    private Map<String, LangContent> langs = new LinkedHashMap<>();

    @Data
    public static class LangContent {
        private String specSummary;
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
    public static class Faq {
        private String question;
        private String answer;
    }
}
