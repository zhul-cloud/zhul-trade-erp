package com.zhul.erp.modules.sales.dto;

import lombok.Data;

import java.time.LocalDate;

/** 销售订单列表查询 */
@Data
public class SalesOrderPageQuery {
    /** 订单编号、PI 编号（带不带前缀都可）、客户、型号 */
    private String keyword;
    private Integer status;
    private LocalDate salesFrom;
    private LocalDate salesTo;
    /** 订单状态：进度码，或 CANCELLED */
    private String progressCode;
    private Integer stockType;
    private Integer customerType;
    private String currencyCode;
    private Long purchaserId;
    private Integer receiptStatus;
    /** true：只看先采购后收款、还没有水单也没有到账的订单 */
    private Boolean payLaterOpen;
    /** 来源：1-PI 转成、2-手动创建、3-售后补发 */
    private Integer source;
    private Long ownerId;
    /** 所属事业部：只在可见订单内缩小范围（UI-015） */
    private Integer businessUnitId;
    private LocalDate createdFrom;
    private LocalDate createdTo;
    private Integer page = 1;
    private Integer pageSize = 20;
    /** 表头排序：profit 按毛利；ascend / descend */
    private String sortField;
    private String sortOrder;
}
