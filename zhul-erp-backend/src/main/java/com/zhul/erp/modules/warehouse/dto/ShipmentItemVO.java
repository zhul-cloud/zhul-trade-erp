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
}
