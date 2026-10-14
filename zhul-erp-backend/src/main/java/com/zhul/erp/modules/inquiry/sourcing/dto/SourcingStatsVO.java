package com.zhul.erp.modules.inquiry.sourcing.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** 询价统计（spec inquiry/sourcing-stats）：按月份（询盘单口径）与按采购员（型号口径） */
@Data
public class SourcingStatsVO {
    private List<MonthRow> months;
    private MonthRow total;
    private List<PurchaserRow> purchasers;

    @Data
    public static class MonthRow {
        private String key;
        private String label;
        private int total;
        private int completed;
        private int unquotable;
        private int inProgress;
        /** 询价完成 ÷ 总数（%） */
        private BigDecimal completedRate;
        /** 无法报价 ÷ 总数（%） */
        private BigDecimal unquotableRate;
    }

    @Data
    public static class PurchaserRow {
        private Long purchaserId;
        private String purchaserName;
        private int items;
        private int priced;
        private int noStock;
        private int pending;
        /** 有价 ÷ 型号数（%） */
        private BigDecimal pricedRate;
    }
}
