package com.zhul.erp.modules.product.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class BrandQuery extends PageQuery {
    /** 按品牌名称模糊匹配 */
    private String keyword;
    private Integer status;
}
