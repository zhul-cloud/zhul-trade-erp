package com.zhul.erp.modules.sales.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 销售订单列表行 */
@Data
public class SalesOrderListVO {
    private Long id;
    private String soNo;
    private Integer source;
    private java.time.LocalDate salesDate;
    private Integer stockType;
    private String progressCode;
    private String progressName;
    /** 采购员姓名（去重，按型号顺序） */
    private java.util.List<String> purchaserNames;
    /** 未指定采购员的型号数 */
    private Integer unassignedCount;
    private Long customerId;
    private String customerName;
    /** 客户国家 */
    private String customerCountry;
    /** 1-新客户、2-老客户（按来源客户询盘判断） */
    private Integer customerType;
    /** 总数量：全部型号数量之和 */
    private Integer totalQuantity;
    private Integer itemCount;
    private String currencyCode;
    private BigDecimal totalAmount;
    private Integer receiptStatus;
    private String receiptStatusName;
    private BigDecimal receivedAmount;
    private Integer status;
    private String statusName;
    private Long piId;
    private String piNo;
    private Integer piVersionNo;
    private Long ownerId;
    private String ownerName;
    private String cancelReason;
    /** 货物状态（件数，不含系统外采购的型号）；已取消的订单为空 */
    private Goods goods;
    private LocalDateTime createTime;
    private String createBy;
    private LocalDateTime updateTime;
    private String updateBy;

    @Data
    public static class Goods {
        private Integer received;
        private Integer inTransit;
        private Integer pendingShip;
        private Integer pendingPurchase;
        private Integer shipped;
        private Integer handed;
        private Integer inWarehouse;
        private Integer total;
        /** 在途最早的预计到货日期 */
        private java.time.LocalDate earliestArrival;
    }
}
