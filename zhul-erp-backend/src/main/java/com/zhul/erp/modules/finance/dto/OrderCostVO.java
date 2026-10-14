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
    private BigDecimal profit;
    /** 毛利率（%）；收入为 0 时为空 */
    private BigDecimal profitRate;
    /** 已结算：已收齐并结汇、全部入库、外币货款付清、国际运费已对账 */
    private Boolean settled;
    /** 还差的项 */
    private List<String> pending;
    /** 没下采购订单又没有采购成本价的型号数 */
    private Integer missingCostCount;
    private List<Line> lines;

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
}
