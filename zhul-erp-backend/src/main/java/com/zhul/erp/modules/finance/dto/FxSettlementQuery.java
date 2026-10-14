package com.zhul.erp.modules.finance.dto;

import lombok.Data;

import java.time.LocalDate;

/** 结汇单列表查询 */
@Data
public class FxSettlementQuery {
    private Integer page = 1;
    private Integer pageSize = 20;
    private String currencyCode;
    private LocalDate dateFrom;
    private LocalDate dateTo;
    private Boolean includeVoid;
}
