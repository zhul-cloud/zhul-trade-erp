package com.zhul.erp.modules.product.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 没有物流信息时返回 id 为空的对象，各数值为 null（不是 0）。 */
@Data
public class LogisticsVO {
    private Long id;
    private Long productId;
    private BigDecimal netWeightKg;
    private BigDecimal grossWeightKg;
    private BigDecimal lengthMm;
    private BigDecimal widthMm;
    private BigDecimal heightMm;
    private String packageType;
    private BigDecimal packageLengthMm;
    private BigDecimal packageWidthMm;
    private BigDecimal packageHeightMm;
    private Integer packageQuantity;
    private Integer isDangerous;
    private String shippingNote;
}
