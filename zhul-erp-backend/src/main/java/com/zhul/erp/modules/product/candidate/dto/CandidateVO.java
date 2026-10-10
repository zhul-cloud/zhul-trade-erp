package com.zhul.erp.modules.product.candidate.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 商品候选；sources 只在详情返回，且只含本公司的来源（平台账号看全部） */
@Data
public class CandidateVO {
    private Long id;
    private Long brandId;
    /** 已识别的品牌名称 */
    private String brandName;
    /** 品牌原文 */
    private String brandText;
    private String mpnRaw;
    private Long categoryId;
    private String categoryName;
    private String categoryText;
    private String productName;
    private String description;
    private String descriptionEn;
    private Integer status;
    private String statusName;
    private Integer level;
    private String levelName;
    /** 询盘次数（来源中的询盘型号数）、成交次数（来源中的订单数）；租户只统计本公司 */
    private Integer inquiryCount;
    private Integer dealCount;
    private LocalDateTime firstSeenAt;
    private LocalDateTime lastSeenAt;
    private Long productId;
    private String productLabel;
    private Integer rejectReason;
    private String rejectReasonName;
    private String rejectNote;
    private String reviewedByName;
    private LocalDateTime reviewedAt;
    /** 回填自哪个询盘原文（真实型号来源时），列表展示用 */
    private String originalModel;
    private List<Source> sources;
    /** 其他公司的来源条数（租户账号看到的） */
    private Integer otherSourceCount;
    private LocalDateTime createTime;
    private String createBy;
    private LocalDateTime updateTime;
    private String updateBy;

    @Data
    public static class Source {
        private Integer sourceType;
        private String sourceTypeName;
        private Long customerInquiryId;
        private String inquiryCode;
        /** 询盘里的原文型号 */
        private String originalModel;
        private Long soId;
        private String soNo;
        private LocalDateTime createTime;
    }
}
