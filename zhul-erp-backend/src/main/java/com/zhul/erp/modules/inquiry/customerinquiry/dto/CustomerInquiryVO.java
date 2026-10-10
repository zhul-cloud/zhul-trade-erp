package com.zhul.erp.modules.inquiry.customerinquiry.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 客户询盘列表行 */
@Data
public class CustomerInquiryVO {
    private Long id;
    private String inquiryCode;
    private Long customerId;
    private String customerName;
    private String customerCountry;
    private Integer customerType;
    private Integer source;
    private LocalDate inquiryDate;
    private LocalDate quoteDeadline;
    private Boolean urgent;
    private Integer level;
    private Integer status;
    private Integer parseMode;
    private Integer totalItemCount;
    private Integer totalQuantity;
    private Integer pricedItemCount;
    private Integer taskCount;
    private Integer timeoutTaskCount;
    private Integer pendingVerifyCount;
    private Boolean needsReview;
    private Long ownerId;
    private String ownerName;
    private Long opportunityId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
