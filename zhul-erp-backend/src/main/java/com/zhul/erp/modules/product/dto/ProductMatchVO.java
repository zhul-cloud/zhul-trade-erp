package com.zhul.erp.modules.product.dto;

import lombok.Data;

import java.util.List;

@Data
public class ProductMatchVO {
    /** 同品牌同归一化型号的商品；没有则为空 */
    private ProductOptionVO exact;
    /** 其他品牌同归一化型号、或归一化型号以输入为前缀的商品，最多 10 条 */
    private List<ProductOptionVO> candidates;
}
