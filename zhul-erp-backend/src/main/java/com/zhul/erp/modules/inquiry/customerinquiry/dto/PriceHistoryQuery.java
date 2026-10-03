package com.zhul.erp.modules.inquiry.customerinquiry.dto;

import lombok.Data;

import java.time.LocalDate;

/** 历史询价查询 */
@Data
public class PriceHistoryQuery {
    private String model;
    private String brand;
    private Integer itemCondition;
    private Integer channel;
    private LocalDate dateFrom;
    private LocalDate dateTo;
    private Integer page = 1;
    private Integer pageSize = 20;
}
