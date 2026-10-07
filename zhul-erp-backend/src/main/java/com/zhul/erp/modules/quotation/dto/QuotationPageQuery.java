package com.zhul.erp.modules.quotation.dto;

import lombok.Data;

import java.time.LocalDate;

/** 报价单列表查询 */
@Data
public class QuotationPageQuery {
    /** 编号、客户、型号 */
    private String keyword;
    private Integer status;
    private String currencyCode;
    private Long ownerId;
    private LocalDate createdFrom;
    private LocalDate createdTo;
    private Integer page = 1;
    private Integer pageSize = 20;
}
