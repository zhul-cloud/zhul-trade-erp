package com.zhul.erp.modules.inquiry.inquiryorder.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class QuoteVO {
    private Long id;
    private Long inquiryOrderItemId;
    private Long inquiryOrderSupplierId;
    private BigDecimal quotePriceOriginal;
    private String currencyCode;
    private BigDecimal exchangeRate;
    /** 汇率未维护时为 null——前端应展示"未计算"，不是0元 */
    private BigDecimal quotePriceCny;
    private String supplierDelivery;
    private Integer quoteStatus;
}
