package com.zhul.erp.modules.product.content.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 商品详情里的内容：任务阶段、资料状态（共享）、本公司英文与俄文营销内容 */
@Data
public class ProductContentVO {
    /** 任务阶段：1-资料待补、2-资料待核、3-营销待生成、4-已完成 */
    private Integer status;
    /** 资料（所有公司共享）：最近导入、核对 */
    private LocalDateTime factImportedAt;
    private String factImportedBy;
    private LocalDateTime factVerifiedAt;
    private String factVerifiedBy;
    /** 本公司营销内容写入时间 */
    private LocalDateTime enAt;
    private LocalDateTime ruAt;
    /** en / ru → 本公司该语言营销内容 */
    private Map<String, LangContent> langs = new LinkedHashMap<>();

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
    public static class Faq {
        private String question;
        private String answer;
    }
}
