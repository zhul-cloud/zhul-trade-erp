package com.zhul.erp.modules.sales.dto;

import lombok.Data;

import java.time.LocalDate;

/** 销售订单列表查询 */
@Data
public class SalesOrderPageQuery {
    /** 订单编号、PI 编号（带不带前缀都可）、客户、型号 */
    private String keyword;
    private Integer status;
    private LocalDate salesFrom;
    private LocalDate salesTo;
    /** 订单状态：进度码，或 CANCELLED */
    private String progressCode;
    private Integer stockType;
    private Integer customerType;
    private String currencyCode;
    private Long purchaserId;
    private Integer receiptStatus;
    private Long ownerId;
    private LocalDate createdFrom;
    private LocalDate createdTo;
    private Integer page = 1;
    private Integer pageSize = 20;
}
