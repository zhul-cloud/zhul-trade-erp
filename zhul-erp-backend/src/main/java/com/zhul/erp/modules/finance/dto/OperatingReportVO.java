package com.zhul.erp.modules.finance.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** 经营统计（spec finance/operating-report）：按年、每月一行与全年合计，金额为人民币 */
@Data
public class OperatingReportVO {
    private Integer year;
    /** 运营成本类别（不含订单费用），按字典顺序 */
    private List<Category> categories;
    private List<Row> rows;
    private Row total;

    @Data
    public static class Category {
        private String code;
        private String name;
    }

    @Data
    public static class Row {
        /** 1–12；全年合计为 0 */
        private Integer month;
        private String label;
        private Integer orderCount;
        private Integer unsettledCount;
        /** 销售收入、销售成本与销售毛利：按销售日期归月，不扣退款 */
        private BigDecimal revenue;
        private BigDecimal cost;
        private BigDecimal profit;
        private BigDecimal profitRate;
        /** 退款：按退款日期归月 */
        private BigDecimal refund;
        private BigDecimal otherIncome;
        /** 运营成本：类别编码 → 分摊后的当月金额 */
        private Map<String, BigDecimal> opex;
        private BigDecimal opexTotal;
        /** 预计盈亏 = 销售毛利 − 退款 + 其他收入 − 运营成本合计 */
        private BigDecimal net;
        /** 净利率 = 预计盈亏 ÷ 销售收入（%） */
        private BigDecimal netRate;
    }
}
