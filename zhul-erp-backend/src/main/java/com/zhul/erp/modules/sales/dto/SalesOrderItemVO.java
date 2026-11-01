package com.zhul.erp.modules.sales.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/** 订单型号行：在 PI 型号行的基础上加跟单信息 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class SalesOrderItemVO extends PiItemVO {
    /** 1-现货、2-期货 */
    private Integer stockType;
    /** 申报单价（原币，仅售后补发订单，CI/PL 用） */
    private java.math.BigDecimal declaredPrice;
    /** 原数量（成交时）与已部分取消的数量；quantity 为剩余数量 */
    private Integer originalQuantity;
    private Integer cancelledQuantity;
    /** 最多还能部分取消的数量 = 剩余数量 − 已出库（含出库中、已交货代、已出运） */
    private Integer cancellableQty;
    /** 已出库（出库中、已交货代、已出运）的数量 */
    private Integer outboundQty;
    private String progressCode;
    private String progressName;
    private Long purchaserId;
    private String purchaserName;
    /** 负责采购的人（来自采购需求，拆分给多人时都在） */
    private java.util.List<String> purchaserNames;
    /** 有采购需求：采购进度来自采购订单 */
    private Boolean purchaseTracked;
    private Integer purchaseOrderedQty;
    /** 合格入库数量（含折价接收） */
    private Integer purchaseReceivedQty;
    /** 货物状态（件数）：已入库、在途、待发货、待采购；系统外采购的型号为空 */
    private Integer goodsReceived;
    private Integer goodsInTransit;
    private Integer goodsPendingShip;
    private Integer goodsPendingPurchase;
    /** 已出运、已交货代（未出运）、在仓（合格入库 − 交货代 − 出运） */
    private Integer goodsShipped;
    private Integer goodsHanded;
    private Integer goodsInWarehouse;
    /** 在途发货单（按预计到货从早到晚） */
    private java.util.List<Transit> transits;
    private Integer purchaseDraftQty;
    private java.util.List<PurchaseRef> purchaseOrders;

    @lombok.Data
    public static class PurchaseRef {
        private Long id;
        /** 草稿为空 */
        private String poNo;
        /** 1-草稿、2-已下单 */
        private Integer status;
        private java.time.LocalDate expectedShipDate;
    }

    @Data
    public static class Transit {
        private Long shipmentId;
        private String sdNo;
        private String carrier;
        private Integer quantity;
        private java.time.LocalDate expectedArrivalDate;
    }
}
