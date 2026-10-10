package com.zhul.erp.modules.logistics.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 销售出库单 */
@Data
public class OutboundVO {
    private Long id;
    private String obNo;
    private Long soId;
    private String soNo;
    private Long customerId;
    private String customerName;
    private String ownerName;
    private Long forwarderId;
    private String forwarderName;
    private Integer source;
    private String sourceName;
    private Integer status;
    private String statusName;
    private String note;
    private String withdrawReason;
    private Integer totalQuantity;
    private List<Item> items;
    private List<Box> boxes;
    private Integer boxCount;
    /** 计费重合计（国内体积系数 5000），两位小数 */
    private BigDecimal chargeableWeight;
    private Courier courier;
    private Long logisticsId;
    private String shNo;
    private String packedByName;
    private LocalDateTime packedAt;
    private LocalDateTime createTime;
    private String createBy;
    private LocalDateTime updateTime;
    private String updateBy;

    @Data
    public static class Item {
        private Long id;
        private Long soItemId;
        private String model;
        private String brand;
        private Integer quantity;
    }

    @Data
    public static class Box {
        private Long id;
        private Integer boxNo;
        private Integer length;
        private Integer width;
        private Integer height;
        private BigDecimal grossWeight;
        private BigDecimal netWeight;
        private BigDecimal chargeable;
        private List<BoxItem> items;
    }

    @Data
    public static class BoxItem {
        private Long outboundItemId;
        private String model;
        private Integer quantity;
    }

    @Data
    public static class Courier {
        private Long id;
        private String carrier;
        private String trackingNo;
        private LocalDate sentDate;
        private BigDecimal freight;
        private String payerName;
        /** 本出库单分到的国内运费 */
        private BigDecimal share;
        /** 共用这票快递的出库单数 */
        private Integer outboundCount;
    }
}
