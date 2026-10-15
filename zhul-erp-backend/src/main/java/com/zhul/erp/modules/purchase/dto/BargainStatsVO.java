package com.zhul.erp.modules.purchase.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 砍价统计（spec purchase/bargain-stats），金额均为人民币（按下单汇率） */
@Data
public class BargainStatsVO {
    private Totals summary;
    private List<Group> groups;

    @Data
    public static class Totals {
        private Integer lineCount;
        /** 采购金额 = 人民币单价 × 数量，不含开票成本 */
        private BigDecimal purchaseAmount;
        /** 有目标价的行：目标价 × 数量 */
        private BigDecimal targetAmount;
        private BigDecimal bargainAmount;
        /** 砍价金额合计 ÷ 目标金额合计（%） */
        private BigDecimal bargainRate;
        private BigDecimal noTargetAmount;
        /** 质量索赔：不良折价接收的折价 */
        private BigDecimal claimAmount;
        private BigDecimal invoiceCost;
    }

    @Data
    public static class Group {
        private String key;
        private String label;
        private Totals totals;
        private List<LineRow> lines;
    }

    @Data
    public static class LineRow {
        private Long poId;
        private String poNo;
        private LocalDate orderDate;
        private String model;
        private String brand;
        private String category;
        private String supplierName;
        private String purchaserName;
        private Integer quantity;
        private BigDecimal targetPrice;
        private BigDecimal netPriceCny;
        private BigDecimal bargainAmount;
        private BigDecimal bargainRate;
        private BigDecimal claimAmount;
    }
}
