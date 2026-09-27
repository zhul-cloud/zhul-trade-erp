package com.zhul.erp.modules.product.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 没有参考价时返回 id 为空的对象。priceCny 为空表示"未计算"，不是 0 元。 */
@Data
public class ReferencePriceVO {
    private Long id;
    private Long productId;
    private BigDecimal priceOriginal;
    private String currencyCode;
    private BigDecimal exchangeRate;
    private BigDecimal priceCny;
    private String priceSource;
    private LocalDate priceDate;
}
