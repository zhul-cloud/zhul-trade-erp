package com.zhul.erp.modules.product.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class BrandVO {
    private Long id;
    private String brandName;
    private String country;
    private String logoUrl;
    private String brandColor;
    private String description;
    private Integer isGenuine;
    private Integer status;
    /** 下面未删除的商品数量 */
    private Long productCount;
    /** 别名 */
    private List<String> aliases;
    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;
}
