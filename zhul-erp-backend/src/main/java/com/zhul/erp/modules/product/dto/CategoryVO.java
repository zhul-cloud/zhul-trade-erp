package com.zhul.erp.modules.product.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CategoryVO {
    private Long id;
    private String categoryCode;
    private String categoryName;
    private String description;
    private Integer sortOrder;
    private Integer status;
    /** 下面未删除的商品数量 */
    private Long productCount;
    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;
}
