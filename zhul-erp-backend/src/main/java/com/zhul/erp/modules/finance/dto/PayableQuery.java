package com.zhul.erp.modules.finance.dto;

import lombok.Data;

/** 应付列表筛选 */
@Data
public class PayableQuery {
    private Integer page;
    private Integer pageSize;
    /** 编号、收款方、来源单号 */
    private String keyword;
    private Integer payeeType;
    /** 约定的付款方式（0-未定、1-对公、2-对私） */
    private Integer payMethod;
    private Integer status;
    /** OVERDUE、SOON、WAITING、MONTH */
    private String due;
    private Boolean hasCredit;
    /** 只看待退款（openspec po-cancel-refund） */
    private Boolean refundExpected;
    private String currencyCode;
    private Boolean showClosed;
    /** UPDATE（默认，更新时间倒序）、DUE（到期日正序） */
    private String sort;
}
