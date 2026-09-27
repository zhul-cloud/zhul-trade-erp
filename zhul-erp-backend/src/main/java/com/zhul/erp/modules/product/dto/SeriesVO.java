package com.zhul.erp.modules.product.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SeriesVO {
    private Long id;
    private Long brandId;
    private String brandName;
    private String seriesName;
    private String description;
    private Integer status;
    /** 下面未删除的商品数量 */
    private Long productCount;
    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;
}
