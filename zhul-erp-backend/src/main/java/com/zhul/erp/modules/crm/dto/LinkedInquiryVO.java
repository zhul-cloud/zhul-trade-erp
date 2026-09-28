package com.zhul.erp.modules.crm.dto;

import lombok.Data;

import java.time.LocalDate;

/** 商机详情里的关联客户询盘 */
@Data
public class LinkedInquiryVO {

    private Long id;
    private String inquiryCode;
    /** 客户询盘状态码，含义同 customer_inquiry.status */
    private Integer status;
    private LocalDate inquiryDate;
    private Integer totalOrderCount;
}
