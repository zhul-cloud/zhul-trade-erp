package com.zhul.erp.modules.inquiry.sourcing.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 无货分析（spec inquiry/sourcing-stats「无货分析」）：所选周 / 月询价过的型号里无货的有多少，按品牌、一级品类分，
 * 加近 8 期走势。同一型号（品牌 + 归一化型号）在一期里询过多次时按最后一次回价算。无货率 = 无货 ÷ 询价型号 × 100，
 * HALF_UP 保留 1 位小数。
 */
@Data
public class NoStockStatsVO {
    /** WEEK 按周、MONTH 按月 */
    private String period;
    private LocalDate dateFrom;
    private LocalDate dateTo;
    private Totals totals;
    /** 上一期，用于较上期 */
    private Totals previous;
    /** 有无货的品牌，按无货数倒序 */
    private List<Group> brands = new ArrayList<>();
    /** 有无货的一级品类，按无货数倒序；没关联品类的归「未分类」 */
    private List<Group> categories = new ArrayList<>();
    /** 近 8 期，从早到晚 */
    private List<Trend> trend = new ArrayList<>();

    @Data
    public static class Totals {
        private int models;
        private int noStock;
        private BigDecimal rate;
        /** 有无货的品牌数 */
        private int brandCount;
        /** 无货最多的品牌 */
        private String topBrand;
        private int topBrandNoStock;
    }

    @Data
    public static class Group {
        /** 品牌：品牌匹配键；品类：一级品类 ID（未分类为空） */
        private String key;
        private Long categoryId;
        private String name;
        private int models;
        private int noStock;
        private BigDecimal rate;
        /** 无货原因，按条数倒序，如「暂时缺货 24」 */
        private List<Reason> reasons = new ArrayList<>();
    }

    @Data
    public static class Reason {
        private String name;
        private int count;
    }

    @Data
    public static class Trend {
        private String label;
        private LocalDate dateFrom;
        private LocalDate dateTo;
        private int models;
        private int noStock;
        private BigDecimal rate;
    }
}
