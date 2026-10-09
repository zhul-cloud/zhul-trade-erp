package com.zhul.erp.modules.purchase.dto;

import com.zhul.erp.modules.masterdata.dto.PaymentTermDTO;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 采购单详情 */
@Data
public class PurchaseOrderVO {
    private Long id;
    private String poNo;
    private Integer status;
    private String statusName;
    private Long supplierId;
    /** 老供应商名称，或「淘宝 · 店铺名」 */
    private String supplierName;
    /** 采购对象为线上店铺：不显示合同，可以转为供应商 */
    private Boolean shop;
    private Integer channel;
    private String shopName;
    private Long purchaserId;
    private String purchaserName;
    private LocalDate orderDate;
    private LocalDateTime orderedAt;
    private LocalDateTime createTime;
    private String currencyCode;
    private BigDecimal exchangeRate;
    private Boolean taxIncluded;
    private BigDecimal taxRate;
    private BigDecimal itemAmount;
    private BigDecimal feeAmount;
    private BigDecimal totalAmount;
    private BigDecimal totalAmountCny;
    private BigDecimal targetAmount;
    private BigDecimal bargainAmount;
    private BigDecimal bargainRate;
    private List<PaymentTermDTO> paymentTerms;
    private String paymentTermsText;
    private String contractNo;
    private BigDecimal contractAmount;
    /** 合同金额 − 合计（原币），合同金额为空时为空 */
    private BigDecimal contractDiff;
    private String cancelReason;
    private String cancelledByName;
    private LocalDateTime cancelledAt;
    /** 草稿或已下单时可改 */
    private Boolean editable;
    private List<Item> items;
    private List<Fee> fees;
    private List<Attachment> attachments;
    private List<Log> logs;
    /** 发货单（含作废的） */
    private List<ShipmentRef> shipments;
    /** 入库单（含冲销的） */
    private List<ReceiptRef> receipts;
    /** 有在途或已入库的发货单（不能取消） */
    private Boolean hasShipments;

    @Data
    public static class Item {
        private Long id;
        private Long requirementId;
        private Long soId;
        private String soNo;
        private String customerName;
        private String customerCountry;
        private String model;
        private String brand;
        private String category;
        private Integer quantity;
        /** 本行最多能改到的数量 = 需求可下单数量 + 本行数量 */
        private Integer maxQuantity;
        private BigDecimal unitPrice;
        private BigDecimal netPriceCny;
        private BigDecimal targetPrice;
        private BigDecimal amount;
        private BigDecimal bargainAmount;
        private BigDecimal bargainRate;
        /** 来源订单已取消 */
        private Boolean orderCancelled;
        /** 已发数量（少发、退回的不算） */
        private Integer shippedQty;
        /** 合格入库数量（含折价接收） */
        private Integer receivedQty;
        /** 未发数量 = 订购 − 已发 */
        private Integer unshippedQty;
    }

    @Data
    public static class Fee {
        private Long id;
        private String feeName;
        private BigDecimal amount;
    }

    @Data
    public static class Attachment {
        private Long id;
        private String fileName;
        private Long fileSize;
        private String contentType;
        private String uploadedByName;
        private LocalDateTime createTime;
    }

    @Data
    public static class Log {
        private String action;
        private String content;
        private String operatorName;
        private LocalDateTime createTime;
    }

    @Data
    public static class ShipmentRef {
        private Long id;
        private String sdNo;
        private LocalDate shipDate;
        private String carrier;
        private String trackingNo;
        private Integer totalQuantity;
        private Integer source;
        private String sourceName;
        private Integer status;
        private String statusName;
    }

    @Data
    public static class ReceiptRef {
        private Long id;
        private String grNo;
        private LocalDate receivedDate;
        private String sdNo;
        private Integer qualifiedQty;
        private Integer defectiveQty;
        private Integer status;
        private String statusName;
    }
}
