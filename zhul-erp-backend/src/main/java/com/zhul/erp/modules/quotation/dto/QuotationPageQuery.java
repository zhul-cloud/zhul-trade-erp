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
    /** 排序字段：itemCount-型号数、totalQuantity-总数量、totalAmount-合计（按折合人民币比较）；不传按创建时间倒序 */
    private String sortField;
    /** ascend / descend */
    private String sortOrder;
    private Integer page = 1;
    private Integer pageSize = 20;
}
