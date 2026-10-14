package com.zhul.erp.modules.finance.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 利润统计（spec finance/profit-stats） */
@Data
public class ProfitStatsVO {
    private Totals summary;
    private List<Group> groups;

    @Data
    public static class Totals {
        private Integer orderCount;
        private Integer customerCount;
        private Integer unsettledCount;
        private BigDecimal salesAmountCny;
        private BigDecimal revenue;
        private BigDecimal cost;
        private BigDecimal profit;
        /** 毛利合计 ÷ 收入合计（%） */
        private BigDecimal profitRate;
        private Integer newOrderCount;
        private Integer oldOrderCount;
        private BigDecimal newRevenue;
        private BigDecimal oldRevenue;
        private BigDecimal newCost;
        private BigDecimal oldCost;
        private BigDecimal newProfit;
        private BigDecimal oldProfit;
        /** 按收款方式拆分的收入：线上、线下，以及还没到账（按销售额计）的部分 */
        private BigDecimal onlineRevenue;
        private BigDecimal offlineRevenue;
        private BigDecimal unreceivedRevenue;
    }

    @Data
    public static class Group {
        private String key;
        private String label;
        /** 按客户分组时的国家 */
        private String country;
        private Totals totals;
        private List<OrderRow> orders;
    }

    @Data
    public static class OrderRow {
        private Long soId;
        private String soNo;
        private String customerName;
        /** 1-新客户、2-老客户 */
        private Integer customerType;
        private LocalDate salesDate;
        private String ownerName;
        private BigDecimal revenue;
        private BigDecimal cost;
        private BigDecimal profit;
        private BigDecimal profitRate;
        private Boolean settled;
        private List<String> pending;
    }
}
