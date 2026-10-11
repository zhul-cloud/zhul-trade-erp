package com.zhul.erp.modules.finance.dto;

import lombok.Data;

import java.time.LocalDate;

/** 付款记录筛选 */
@Data
public class PaymentQuery {
    private Integer page;
    private Integer pageSize;
    private String keyword;
    private LocalDate dateFrom;
    private LocalDate dateTo;
    private Integer method;
    private String advanceUser;
    private String currencyCode;
    private Integer kind;
    private Boolean showVoid;
}
