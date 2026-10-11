package com.zhul.erp.modules.finance.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class ReimbursementQuery {
    private Integer page;
    private Integer pageSize;
    private String keyword;
    private Integer status;
    private Long applicantId;
    private LocalDate dateFrom;
    private LocalDate dateTo;
}
