package com.zhul.erp.modules.product.dto;

import lombok.Data;

@Data
public class SaveApplicationRequest {
    private String title;
    private String description;
    /** 图标，直接存 emoji 或图标标识 */
    private String icon;
    private Integer verified;
    private Integer sortOrder;
}
