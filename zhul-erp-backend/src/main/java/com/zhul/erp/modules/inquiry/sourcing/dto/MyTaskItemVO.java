package com.zhul.erp.modules.inquiry.sourcing.dto;

import lombok.Data;

import java.util.List;

/** 我的询价任务中的一个型号 */
@Data
public class MyTaskItemVO {
    private Long id;
    private String model;
    private String originalModel;
    private Integer quantity;
    private String unit;
    private String description;
    private Integer lifecycle;
    private String replacementModel;
    /** 采购回填的真实型号 */
    private String actualModel;
    /** 建档状态：0-未处理、1-已建档、2-候选中、3-待回填真实型号 */
    private Integer archiveStatus;
    private String archiveStatusName;
    /** 已建档时关联的商品 */
    private Long productId;
    private Integer difficulty;
    private String inquiryScript;
    private List<String> searchKeywords;
    private List<MyQuoteVO> quotes;
    /** 已报给客户（出现在已发送及之后的报价单中），回价只读 */
    private Boolean locked;
    /** 兼职回价的审核状态：1-待审核、2-已通过、3-被退回；不需要审核或还没提交时为空 */
    private Integer reviewStatus;
    /** 被退回时的退回原因 */
    private String reviewNote;
    private String reviewedByName;
    private java.time.LocalDateTime reviewedAt;
}
