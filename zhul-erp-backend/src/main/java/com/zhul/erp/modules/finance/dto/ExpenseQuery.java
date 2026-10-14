package com.zhul.erp.modules.finance.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class ExpenseQuery {
    private Integer page;
    private Integer pageSize;
    /** 编号、名称、收款方 */
    private String keyword;
    /** 关联的销售订单号 */
    private String soNo;
    private LocalDate dateFrom;
    private LocalDate dateTo;
    private Integer method;
    private Boolean showVoid;
}
