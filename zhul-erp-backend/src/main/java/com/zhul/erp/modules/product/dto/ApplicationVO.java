package com.zhul.erp.modules.product.dto;

import lombok.Data;

@Data
public class ApplicationVO {
    private Long id;
    private Long productId;
    private String title;
    private String description;
    private String icon;
    private Integer verified;
    private Integer sortOrder;
}
