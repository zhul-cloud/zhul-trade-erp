package com.zhul.erp.modules.product.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class SeriesQuery extends PageQuery {
    private Long brandId;
    /** 按系列名称模糊匹配 */
    private String keyword;
    private Integer status;
}
