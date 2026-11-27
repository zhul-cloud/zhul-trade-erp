package com.zhul.erp.modules.website.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 发布预览里的一个商品 */
@Data
public class PreviewVO {

    private Long taskId;
    private Long websiteProductId;
    private Long productId;
    private String brand;
    private String model;
    private String slug;
    private String siteCategory;
    private String siteStatus;
    /** 语言代码 → 上线后的链接 */
    private Map<String, String> liveUrls = new LinkedHashMap<>();
    /** 语言代码 → SEO 标题（站点数据不带标题，只用于检查和显示） */
    private Map<String, String> seoTitles = new LinkedHashMap<>();
    private Price price;
    /** 用的是参考型号的素材 */
    private boolean referenceMedia;
    /** 写入站点的数据（发布单号除外） */
    private Map<String, Object> data;
    private List<String> blockers = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
    private boolean firstPublish;
    private List<Change> changes = new ArrayList<>();

    public boolean isPublishable() {
        return blockers.isEmpty();
    }

    @Data
    public static class Price {
        private BigDecimal amount;
        private String currency;
        private String source;
        private String sourceName;
        private Long quoteId;
        private BigDecimal unitPriceCny;
        private BigDecimal factor;
        private BigDecimal rate;
        private LocalDate rateDate;
    }

    /** 与上次发布相比变化的字段 */
    public record Change(String field, JsonNode before, JsonNode after) {
    }
}
