package com.zhul.erp.modules.product.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CustomsVO {
    private Long id;
    private Long productId;
    private String hsCode;
    private String customsNameCn;
    private String customsNameEn;
    private String originCountry;
    private String declarationElements;
    private String supervisionConditions;
    private BigDecimal exportRebateRate;
}
