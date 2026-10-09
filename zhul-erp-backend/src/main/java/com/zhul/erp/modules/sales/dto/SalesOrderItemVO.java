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
    private String progressCode;
    private String progressName;
    private Long purchaserId;
    private String purchaserName;
    /** 负责采购的人（来自采购需求，拆分给多人时都在） */
    private java.util.List<String> purchaserNames;
    /** 有采购需求：采购进度来自采购单 */
    private Boolean purchaseTracked;
    private Integer purchaseOrderedQty;
    /** 合格入库数量（含折价接收） */
    private Integer purchaseReceivedQty;
    private Integer purchaseDraftQty;
    private java.util.List<PurchaseRef> purchaseOrders;

    @lombok.Data
    public static class PurchaseRef {
        private Long id;
        /** 草稿为空 */
        private String poNo;
        /** 1-草稿、2-已下单 */
        private Integer status;
    }
}
