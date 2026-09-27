package com.zhul.erp.modules.product.dto;

import lombok.Data;

import java.util.List;

@Data
public class BrandOptionVO {
    private Long id;
    private String brandName;
    private String logoUrl;
    private String description;
    private Integer isGenuine;
    /** 别名，前端选择品牌时可按别名搜索 */
    private List<String> aliases;
}
