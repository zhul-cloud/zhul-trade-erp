package com.zhul.erp.modules.quotation.dto;

import lombok.Data;

import java.time.LocalDate;

/** 可报价 / 询价中的客户询盘卡片 */
@Data
public class QuoteInquiryVO {
    private Long inquiryId;
    private String inquiryCode;
    private Long customerId;
    private String customerName;
    /** 新老客户（1-新客户、2-老客户） */
    private Integer customerType;
    private LocalDate inquiryDate;
    private LocalDate quoteDeadline;
    private Boolean urgent;
    private Integer level;
    /** 询盘状态（5-询价中、6-可报价、7-已报价） */
    private Integer status;
    private Integer itemCount;
    /** 已有价格（含无货）的型号数 */
    private Integer pricedCount;
    /** 已在草稿报价单中时的编号 */
    private String draftQuotationNo;
    /** 已有草稿时点击直接打开这张草稿 */
    private Long draftQuotationId;
}
