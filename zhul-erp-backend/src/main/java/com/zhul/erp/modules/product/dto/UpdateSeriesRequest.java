package com.zhul.erp.modules.product.dto;

import lombok.Data;

/** 系列创建后不能换品牌（商品的系列必须属于商品的品牌），所以修改入参里没有 brandId。 */
@Data
public class UpdateSeriesRequest {
    private String seriesName;
    private String description;
}
