package com.zhul.erp.modules.warehouse.dto;

import lombok.Data;
import java.time.LocalDate;

/** 供应商发货列表 / 入库验收待收货的查询 */
@Data
public class ShipmentPageQuery {
    private Integer page;
    private Integer pageSize;
    /** 发货单号、采购单号、快递单号、型号、采购对象 */
    private String keyword;
    private Integer status;
    private LocalDate shipFrom;
    private LocalDate shipTo;
}
