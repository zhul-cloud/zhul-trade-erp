package com.zhul.erp.modules.inquiry.customerinquiry.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 一条询价记录（询价记录） */
@Data
public class PriceRecordVO {
    private Long id;
    private String brand;
    private String model;
    private Integer channel;
    private String shopName;
    private Boolean noStock;
    /** 是否包邮 */
    private Boolean freeShipping;
    /** 回价附件（截图、报价单），与渠道店铺一样只对有「查看货源信息」权限的人返回 */
    private java.util.List<com.zhul.erp.modules.attachment.dto.AttachmentVO> attachments;
    private String currencyCode;
    private BigDecimal unitPrice;
    private BigDecimal unitPriceCny;
    private Boolean taxIncluded;
    /** 税率百分比；unitPrice 为采购填的含税价，unitPriceCny 为换算后的不含税价 */
    private BigDecimal taxRate;
    private Integer itemCondition;
    private Integer leadTime;
    private String note;
    private Boolean recommended;
    private Long quotedBy;
    private String quotedByName;
    private LocalDateTime quotedAt;
    /** 距今天数 */
    private Long daysAgo;
    private Long customerInquiryId;
    private String customerInquiryCode;
}
