package com.zhul.erp.modules.warehouse.dto;

import lombok.Data;
import java.time.LocalDate;

/** 已入库列表的查询 */
@Data
public class ReceiptPageQuery {
    private Integer page;
    private Integer pageSize;
    /** 入库单号、发货单号、采购单号、快递单号、型号、采购对象 */
    private String keyword;
    private Integer status;
    private LocalDate receivedFrom;
    private LocalDate receivedTo;
}
