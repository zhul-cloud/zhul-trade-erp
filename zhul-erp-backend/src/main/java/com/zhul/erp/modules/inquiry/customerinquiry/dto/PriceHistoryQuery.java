package com.zhul.erp.modules.inquiry.customerinquiry.dto;

import lombok.Data;

import java.time.LocalDate;

/** 询价记录查询 */
@Data
public class PriceHistoryQuery {
    private String model;
    private String brand;
    private Integer itemCondition;
    private Integer channel;
    /** 回价结果：true 只看无货、false 只看有价，不传为全部 */
    private Boolean noStock;
    /** 一级品类：型号明细关联到这个品类或它的细分品类 */
    private Long categoryId;
    private LocalDate dateFrom;
    private LocalDate dateTo;
    private Integer page = 1;
    private Integer pageSize = 20;
}
