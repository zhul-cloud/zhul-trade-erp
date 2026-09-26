package com.zhul.erp.modules.product.dto;

import lombok.Data;

@Data
public class SaveCategoryRequest {
    /** 品类编码：小写字母开头，只含小写字母、数字、下划线，≤32；有商品后不可修改 */
    private String categoryCode;
    private String categoryName;
    /** 中文名称，≤32；细分品类必填 */
    private String categoryNameZh;
    /** 上级品类ID：为空创建一级品类，否则创建该一级品类下的细分品类 */
    private Long parentId;
    /** 品类简介，≤500 字；可空 */
    private String description;
    private Integer sortOrder;
}
