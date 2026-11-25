package com.zhul.erp.modules.inquiry.sourcing.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 品牌品类统计（spec inquiry/sourcing-stats「品牌品类统计」）：一期内询盘日期落在期内、已进入询价、未取消的询盘单的型号行，
 * 按品牌、一级品类汇总。有价率 = 有价 ÷ 询价型号数（百分比，HALF_UP 保留 2 位）；平均回价时长为任务首次分配到型号第一条
 * 已提交回价的小时数（HALF_UP 保留 1 位），只算有回价的型号。
 */
@Data
public class BrandCategoryStatsVO {
    /** DAY / WEEK / MONTH / QUARTER / YEAR */
    private String period;
    private LocalDate dateFrom;
    private LocalDate dateTo;
    private Row totals;
    /** 上一期，用于较上期 */
    private Row previous;
    /** 按询价型号数倒序 */
    private List<Row> brands = new ArrayList<>();
    /** 一级品类，按询价型号数倒序；没关联品类的归「未分类」 */
    private List<Row> categories = new ArrayList<>();

    @Data
    public static class Row {
        private String key;
        private String name;
        /** 一级品类 ID，品牌行与「未分类」为空 */
        private Long categoryId;
        private int items;
        private int priced;
        private int noStock;
        private int pending;
        private BigDecimal pricedRate;
        private BigDecimal avgHours;
    }
}
