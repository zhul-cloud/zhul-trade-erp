package com.zhul.erp.modules.product.dto;

import lombok.Data;

/** 选择器 / 匹配用的精简商品信息，不含规格等大字段。 */
@Data
public class ProductOptionVO {
    private Long id;
    private String mpnDisplay;
    private String brandName;
    private String categoryName;
    private String productName;
}
