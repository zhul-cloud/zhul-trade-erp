package com.zhul.erp.modules.warehouse.dto;

import lombok.Data;

/** 发货单行 */
@Data
public class ShipmentItemVO {
    private Long id;
    private Long poItemId;
    private String model;
    private String brand;
    private String category;
    private Integer quantity;
    /** 详情页：采购订单这一行的数量、这张发货单在有效入库单上的实收数量 */
    private Integer orderQty;
    private Integer receivedQty;
}
