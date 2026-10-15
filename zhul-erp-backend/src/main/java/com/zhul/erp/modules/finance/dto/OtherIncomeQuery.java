package com.zhul.erp.modules.finance.dto;

import lombok.Data;

import java.time.LocalDate;

/** 其他收入列表查询 */
@Data
public class OtherIncomeQuery {
    private Integer page = 1;
    private Integer pageSize = 20;
    private String category;
    private LocalDate dateFrom;
    private LocalDate dateTo;
    private Boolean showVoid;
}
