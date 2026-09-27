package com.zhul.erp.modules.product.dto;

import lombok.Data;

@Data
public class CategoryOptionVO {
    private Long id;
    private String categoryCode;
    private String categoryName;
    private String categoryNameZh;
    /** 上级品类ID，一级品类为空 */
    private Long parentId;
    private String description;
}
