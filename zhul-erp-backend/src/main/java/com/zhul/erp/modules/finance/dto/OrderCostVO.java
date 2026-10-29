package com.zhul.erp.modules.finance.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** 订单实际成本与毛利（spec finance/order-cost），金额均为人民币 */
@Data
public class OrderCostVO {
    private Long soId;
    /** 销售额折人民币 */
    private BigDecimal salesAmountCny;
    /** 收入：已到账为毛额 × 到账汇率 + 中转差额，否则为销售额折人民币 */
    private BigDecimal revenue;
    private BigDecimal cost;
    private BigDecimal receiptFee;
    /** 有效退款单的人民币合计（从收入中扣减） */
    private BigDecimal refund;
    private List<RefundBrief> refunds;
    /** 毛利 = 收入 − 退款 − 实际成本 */
    private BigDecimal profit;
    /** 毛利率（%）= 毛利 ÷（收入 − 退款）；为 0 时为空 */
    private BigDecimal profitRate;
    /** 已结算：已收齐并结汇、全部入库、外币货款付清、国际运费已对账 */
    private Boolean settled;
    /** 还差的项 */
    private List<String> pending;
    /** 没下采购订单又没有采购成本价的型号数 */
    private Integer missingCostCount;
    private List<Line> lines;
    /** 历史迁移订单：收入、成本、毛利与结算状态取旧系统《成本核算》的快照，不按入库与对账推算 */
    private Boolean historySnapshot;
    /** 快照的各项金额（historySnapshot 为 true 时有值） */
    private Snapshot snapshot;

    @Data
    public static class Snapshot {
        private BigDecimal purchaseAmount;
        private BigDecimal freight;
        private BigDecimal surcharge;
        private BigDecimal serviceFee;
        /** 核算月份 YYYY-MM */
        private String accountingMonth;
        /** 来源核算单旧编号 */
        private String sourceCodes;
    }

    @Data
    public static class Line {
        private Long soItemId;
        private String model;
        private String brand;
        private Integer quantity;
        private BigDecimal purchase;
        /** 采购货款已确定（全部入库、外币已付清） */
        private Boolean purchaseSettled;
        private BigDecimal invoice;
        private BigDecimal poFee;
        /** 质量索赔（不良折价，冲减成本） */
        private BigDecimal claim;
        private BigDecimal returnFreight;
        private BigDecimal domestic;
        private BigDecimal international;
        private Boolean internationalSettled;
        private BigDecimal expense;
        private BigDecimal receiptFee;
        private BigDecimal cost;
        private BigDecimal revenue;
        private BigDecimal refund;
        private BigDecimal profit;
        private BigDecimal profitRate;
        private Integer unreceivedQty;
        private Integer unorderedQty;
        private Boolean missingCostPrice;
        /** 各成本项的来源单据：purchase、domestic、international、expense */
        private Map<String, List<Source>> sources;
    }

    @Data
    public static class Source {
        /** PO 采购订单、COURIER 快递单、SHIPMENT 出运单、EXPENSE 费用单 */
        private String type;
        private Long id;
        private String no;
        private BigDecimal amount;
        private String note;
    }

    @Data
    public static class RefundBrief {
        private Long id;
        private String refundNo;
        private java.time.LocalDate refundDate;
        private String reasonName;
        private String currencyCode;
        private BigDecimal amount;
        private BigDecimal amountCny;
    }
}
