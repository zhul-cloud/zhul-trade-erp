package com.zhul.erp.modules.sales.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** 销售统计（spec sales/sales-stats）：金额为人民币，比率为百分比（两位小数，分母为 0 时为空） */
@Data
public class SalesStatsVO {
    private List<Row> rows;
    private Row total;
    /** 按客户视图 */
    private List<CustomerRow> customers;

    /** 新客户与老客户拆分的计数 */
    @Data
    public static class Split {
        private int all;
        private int fresh;
        private int old;
    }

    /** 新客户与老客户拆分的金额 */
    @Data
    public static class Money {
        private BigDecimal all;
        private BigDecimal fresh;
        private BigDecimal old;
    }

    @Data
    public static class Row {
        private String key;
        private String label;
        private int opportunities;
        private Split inquiries;
        private Split quotations;
        private Split intents;
        private Money intentAmount;
        private Split pending;
        private Money pendingAmount;
        private Split orders;
        private Split customers;
        private Money revenue;
        private Money profit;
        private BigDecimal profitRate;
        private BigDecimal profitRateNew;
        private BigDecimal profitRateOld;
        /** 询盘数 ÷ 商机数 */
        private BigDecimal inquiryRate;
        private BigDecimal inquiryRateNew;
        /** 订单数 ÷ 询盘数 */
        private BigDecimal orderRate;
        private BigDecimal orderRateNew;
        private BigDecimal orderRateOld;
        /** 订单数 ÷ 商机数 */
        private BigDecimal overallRate;
        /** 收入 ÷ 订单数 */
        private BigDecimal avgPrice;
        private BigDecimal avgPriceNew;
        private BigDecimal avgPriceOld;
        /** 老客户订单数 ÷ 订单数 */
        private BigDecimal orderRepeatRate;
        /** 老成交客户数 ÷ 成交客户数 */
        private BigDecimal customerRepeatRate;
    }

    @Data
    public static class CustomerRow {
        private Long customerId;
        private String customerName;
        private String country;
        /** 1-新客户、2-老客户（期间内首张订单的标记） */
        private Integer customerType;
        private int orders;
        private BigDecimal revenue;
        private BigDecimal profit;
        private BigDecimal profitRate;
    }
}
