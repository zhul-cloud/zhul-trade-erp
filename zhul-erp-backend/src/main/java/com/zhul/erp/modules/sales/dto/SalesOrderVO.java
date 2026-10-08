package com.zhul.erp.modules.sales.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 销售订单详情：内容只读，收款记录来自 PI */
@Data
public class SalesOrderVO {
    private Long id;
    private String soNo;
    /** 1-PI 转成、2-手动创建 */
    private Integer source;
    private java.time.LocalDate salesDate;
    /** 1-新客户、2-老客户 */
    private Integer customerType;
    /** 1-现货、2-期货 */
    private Integer stockType;
    private String progressCode;
    /** 订单状态显示名：已取消，或进度名称 */
    private String progressName;
    private LocalDateTime completedAt;
    /** 有效且未完成：可以改跟单信息 */
    private Boolean trackable;
    /** 所有型号都到达最后一步：可以确认客户收货 */
    private Boolean completable;
    /** 进行中的步骤（按推进顺序，含停用的） */
    private List<Step> steps;
    /** 采购员与负责的型号数（未指定的 userId 为空） */
    private List<Purchaser> purchasers;
    /** 收款按付款方式汇总（有效到账，线上在前） */
    private List<MethodTotal> methodTotals;
    private Margin margin;
    private Long piId;
    private String piNo;
    private Integer piVersionNo;
    private Integer piStatus;
    private Long customerId;
    private String customerName;
    private Long ownerId;
    private String ownerName;
    private String currencyCode;
    private BigDecimal exchangeRate;
    private PartyDTO buyer;
    private PartyDTO consignee;
    private String deliveryTime;
    private String paymentTerm;
    private String incoterm;
    private String incotermPlace;
    private String portOfShipment;
    private String remark;
    private BigDecimal itemAmount;
    private BigDecimal feeAmount;
    private BigDecimal discountAmount;
    private BigDecimal totalAmount;
    private BigDecimal totalAmountCny;
    private BigDecimal netProfit;
    private BigDecimal netProfitCny;
    private BigDecimal marginRate;
    /** 状态（1-有效、2-已取消） */
    private Integer status;
    private String statusName;
    private String cancelReason;
    private String cancelledByName;
    private LocalDateTime cancelledAt;
    private Integer receiptStatus;
    private String receiptStatusName;
    private BigDecimal receivedAmount;
    private BigDecimal feeDiffAmount;
    private BigDecimal remainingAmount;
    private List<SalesOrderItemVO> items;
    private List<PiFeeVO> fees;
    private List<ReceiptVO> receipts;
    private LocalDateTime createTime;
    private String createByName;

    @Data
    public static class Step {
        private String code;
        private String name;
        private Boolean enabled;
    }

    @Data
    public static class Purchaser {
        private Long userId;
        private String name;
        private Integer itemCount;
    }

    @Data
    public static class MethodTotal {
        private String paymentMethod;
        private String paymentMethodName;
        private Integer channel;
        private String currencyCode;
        private BigDecimal amount;
    }

    /** 订单毛利（CNY）：收齐款后为 实收人民币 − 采购成本，否则为预计（销售额折合人民币 − 采购成本） */
    @Data
    public static class Margin {
        private BigDecimal salesAmountCny;
        private BigDecimal receivedCny;
        private BigDecimal costCny;
        private BigDecimal profitCny;
        private Boolean estimated;
        /** 没有采购成本价的型号数 */
        private Integer missingCostCount;
    }
}
