package com.zhul.erp.modules.product.dto;

import lombok.Data;

import java.util.List;

@Data
public class SaveBrandRequest {
    private String brandName;
    /** 原产地，取自国家清单（GET /product/countries）的英文名；可空 */
    private String country;
    private String logoUrl;
    /** 品牌主题色，HEX，如 #009999；可空 */
    private String brandColor;
    /** 品牌简介，≤500 字；可空 */
    private String description;
    /** 是否原厂正品（0-兼容/非原厂、1-原厂正品）；创建时缺省为 1，修改时缺省为不变 */
    private Integer isGenuine;
    /** 别名（每个 ≤64）；为 null 表示不修改，空列表表示清空 */
    private List<String> aliases;
}
