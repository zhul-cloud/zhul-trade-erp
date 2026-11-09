package com.zhul.erp.modules.sales.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 销售订单列表「查看」弹窗的一行型号 */
@Data
public class SalesOrderModelVO {
    private Long id;
    private String brand;
    private String model;
    private String category;
    private Integer quantity;
    private BigDecimal unitPrice;
    private String currencyCode;
}
