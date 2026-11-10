package com.zhul.erp.modules.inquiry.customerinquiry.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 询盘单列表行 */
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
    /** 解析中且分批解析时的进度：已解析批数 / 总批数；不分批或不在解析中时为空 */
    private Integer parseProgressDone;
    private Integer parseProgressTotal;
    private Integer totalItemCount;
    private Integer totalQuantity;
    /** 第一个型号；未解析、解析中、解析失败时为空 */
    private String firstModel;
    /** 品牌、一级品类，按型号数从多到少 */
    private List<String> brands;
    private List<String> categories;
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
    /** 历史迁移的单据（只读） */
    private Boolean migrated;
}
