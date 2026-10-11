package com.zhul.erp.modules.product.dto;

import lombok.Data;

@Data
public class SaveApplicationRequest {
    /** 语言（zh、en、ru），不传为英文；修改时不可改语言 */
    private String lang;
    private String title;
    private String description;
    /** 图标，直接存 emoji 或图标标识 */
    private String icon;
    private Integer verified;
    private Integer sortOrder;
}
