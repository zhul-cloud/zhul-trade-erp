package com.zhul.erp.modules.purchase.dto;

import lombok.Data;

import java.time.LocalDate;

/** 砍价统计查询：下单日期范围（默认本年）、采购员、供应商、品牌、品类、分组方式 */
@Data
public class BargainStatsQuery {
    private LocalDate dateFrom;
    private LocalDate dateTo;
    private Long purchaserId;
    private Long supplierId;
    private String brand;
    private String category;
    /** PURCHASER 采购员（默认）、SUPPLIER 供应商或店铺、BRAND 品牌、CATEGORY 品类、MONTH 月份 */
    private String groupBy;
}
