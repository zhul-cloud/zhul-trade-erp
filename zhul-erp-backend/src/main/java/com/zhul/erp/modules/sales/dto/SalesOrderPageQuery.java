package com.zhul.erp.modules.sales.dto;

import lombok.Data;

import java.time.LocalDate;

/** 销售订单列表查询 */
@Data
public class SalesOrderPageQuery {
    /** 订单编号、PI 编号（带不带前缀都可）、客户、型号 */
    private String keyword;
    private Integer status;
    private Integer receiptStatus;
    private Long ownerId;
    private LocalDate createdFrom;
    private LocalDate createdTo;
    private Integer page = 1;
    private Integer pageSize = 20;
}
