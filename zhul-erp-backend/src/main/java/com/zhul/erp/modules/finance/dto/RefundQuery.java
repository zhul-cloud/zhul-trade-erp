package com.zhul.erp.modules.finance.dto;

import lombok.Data;

import java.time.LocalDate;

/** 退款单列表查询 */
@Data
public class RefundQuery {
    private Integer page = 1;
    private Integer pageSize = 20;
    /** 订单号或客户 */
    private String keyword;
    private String reason;
    private Long soId;
    private LocalDate dateFrom;
    private LocalDate dateTo;
    private Boolean showVoid;
}
