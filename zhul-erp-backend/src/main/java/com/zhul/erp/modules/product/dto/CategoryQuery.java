package com.zhul.erp.modules.product.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class CategoryQuery extends PageQuery {
    /** 按品类编码或名称模糊匹配 */
    private String keyword;
    private Integer status;
    /** 上级品类ID：为空时只查一级品类 */
    private Long parentId;
}
