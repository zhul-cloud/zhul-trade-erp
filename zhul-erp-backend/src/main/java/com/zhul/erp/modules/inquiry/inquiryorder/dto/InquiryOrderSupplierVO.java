package com.zhul.erp.modules.inquiry.inquiryorder.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class InquiryOrderSupplierVO {
    private Long id;
    private Long inquiryOrderId;
    private Integer sourceType;
    private Long supplierId;
    private String supplierName;
    private Integer channelPlatform;
    private String channelName;
    private String channelLink;
    private LocalDate sentDate;
    private LocalDate replyDeadline;
    private Integer status;
    private String quoteFileUrl;
    private String remark;

    /** 报价对比/供应商卡片展示用的统一名称：正式供应商用 supplierName，电商渠道用 "平台·店铺名" */
    private String displayName;
}
