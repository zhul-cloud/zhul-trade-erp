package com.zhul.erp.modules.quotation.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 报价单费用行 */
@Data
public class QuotationFeeVO {
    private String feeName;
    private BigDecimal amount;
    private BigDecimal amountCny;
}
