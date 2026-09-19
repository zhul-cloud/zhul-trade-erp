package com.zhul.erp.modules.product.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CategoryVO {
    private Long id;
    private String categoryCode;
    private String categoryName;
    private Integer sortOrder;
    private Integer status;
    /** 下面未删除的商品数量 */
    private Long productCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
