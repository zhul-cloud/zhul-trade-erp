package com.zhul.erp.modules.inquiry.inquiryorder.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class QuoteComparisonCellVO {
    private Long inquiryOrderSupplierId;
    /** true 表示尚无报价记录或 quotePriceCny 为空，前端展示"未报价" */
    private boolean quoted;
    private BigDecimal quotePriceCny;
    private String currencyCode;
    private BigDecimal quotePriceOriginal;
    private String supplierDelivery;
    /** 该型号跨来源比较后，是否为最低本位币价 */
    private boolean lowestPrice;
}
