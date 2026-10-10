package com.zhul.erp.modules.logistics.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/** 直发货代的供应商发货单（出运单上待确认 / 已确认） */
@Data
public class DirectRef {
    private Long id;
    private String sdNo;
    private Long poId;
    private String poNo;
    private String supplierName;
    private Long customerId;
    private String customerName;
    private List<String> soNos;
    private String carrier;
    private String trackingNo;
    private LocalDate shipDate;
    private LocalDate expectedArrivalDate;
    /** 已按货代实收确认 */
    private Boolean confirmed;
    /** 确认后生成的出库单 */
    private Long outboundId;
    private List<Line> items;

    @Data
    public static class Line {
        private Long shipmentItemId;
        private String model;
        private String brand;
        private Integer quantity;
    }
}
