package com.zhul.erp.modules.product.dto;

import lombok.Data;

@Data
public class BrandOptionVO {
    private Long id;
    private String brandName;
    private String logoUrl;
    private Integer isGenuine;
}
