package com.zhul.erp.modules.product.dto;

import lombok.Data;

@Data
public class SaveCategoryRequest {
    /** 品类编码：小写字母开头，只含小写字母、数字、下划线，≤32；有商品后不可修改 */
    private String categoryCode;
    private String categoryName;
    private Integer sortOrder;
}
