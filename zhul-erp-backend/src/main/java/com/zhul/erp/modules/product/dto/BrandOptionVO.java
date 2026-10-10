package com.zhul.erp.modules.product.dto;

import lombok.Data;

import java.util.List;

@Data
public class BrandOptionVO {
    private Long id;
    private String brandName;
    private String logoUrl;
    private String description;
    private String descriptionZh;
    /** 0-普通、1-常做、2-核心；选项按等级、名称排序 */
    private Integer brandLevel;
    private Integer isGenuine;
    /** 别名，前端选择品牌时可按别名搜索 */
    private List<String> aliases;
}
