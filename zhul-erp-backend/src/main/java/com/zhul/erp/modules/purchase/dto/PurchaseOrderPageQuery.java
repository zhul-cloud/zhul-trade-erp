package com.zhul.erp.modules.purchase.dto;

import lombok.Data;

import java.time.LocalDate;

/** 采购单列表查询 */
@Data
public class PurchaseOrderPageQuery {
    private Integer page;
    private Integer pageSize;
    /** 采购单编号、供应商、型号、订单编号 */
    private String keyword;
    /** 1-草稿、2-已下单、3-已取消 */
    private Integer status;
    private Long supplierId;
    private Long purchaserId;
    /** 下单日期范围（草稿没有下单日期，有范围时不列出） */
    private LocalDate orderFrom;
    private LocalDate orderTo;
    /** itemCount / totalQuantity / totalAmount / bargainAmount */
    private String sortField;
    /** ascend / descend */
    private String sortOrder;
}
