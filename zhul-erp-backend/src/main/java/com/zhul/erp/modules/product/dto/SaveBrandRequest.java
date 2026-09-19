package com.zhul.erp.modules.product.dto;

import lombok.Data;

@Data
public class SaveBrandRequest {
    private String brandName;
    private String country;
    private String logoUrl;
    /** 品牌主题色，HEX，如 #009999；可空 */
    private String brandColor;
    /** 是否原厂正品（0-兼容/非原厂、1-原厂正品）；创建时缺省为 1，修改时缺省为不变 */
    private Integer isGenuine;
}
