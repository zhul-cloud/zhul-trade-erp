package com.zhul.erp.modules.product.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 平台参考价（资料包导入的公开挂牌价） */
@Data
public class ListingPriceVO {
    private Long id;
    private String category;
    private String platform;
    private String itemCondition;
    private BigDecimal priceUsd;
    private String sourcePrice;
    private String linkTitle;
    private String linkUrl;
    private LocalDate priceDate;
}
