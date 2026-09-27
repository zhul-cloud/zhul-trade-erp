package com.zhul.erp.modules.product.dto;

import lombok.Data;

@Data
public class SpecificationVO {
    private Long id;
    private String specKey;
    private String specLabel;
    private String specValue;
    private String specUnit;
    private String source;
    private Integer verified;
    private Integer sortOrder;
}
