package com.zhul.erp.modules.warehouse.dto;

import lombok.Data;

import java.time.LocalDate;

/** 订单型号行在某张在途发货单上的数量 */
@Data
public class TransitRow {
    private Long soItemId;
    private Long shipmentId;
    private String sdNo;
    private String carrier;
    private Integer quantity;
    private LocalDate expectedArrivalDate;
}
