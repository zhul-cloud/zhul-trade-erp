package com.zhul.erp.modules.product.dto;

import lombok.Data;

@Data
public class CreateSeriesRequest {
    private Long brandId;
    private String seriesName;
    private String description;
}
