package com.zhul.erp.modules.sales.dto;

import lombok.Data;

import java.time.LocalDate;

/** 收款记录筛选 */
@Data
public class ReceiptRecordQuery {
    private String keyword;
    private LocalDate dateFrom;
    private LocalDate dateTo;
    private String paymentMethod;
    /** 1-线下、2-线上 */
    private Integer channel;
    private String currencyCode;
    /** 同时列出已作废的到账 */
    private Boolean includeVoid;
    private Integer page = 1;
    private Integer pageSize = 20;
}
