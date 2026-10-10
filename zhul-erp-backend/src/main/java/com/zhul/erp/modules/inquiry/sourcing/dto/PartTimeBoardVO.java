package com.zhul.erp.modules.inquiry.sourcing.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 兼职工作台：本人询价统计（本月按自然月，趋势为近 30 天） */
@Data
public class PartTimeBoardVO {
    /** 待处理任务数（询价中、本人有效分配） */
    private Integer pendingTasks;
    /** 其中已超时 */
    private Integer timeoutTasks;
    /** 其中紧急 */
    private Integer urgentTasks;
    /** 本月回价的型号数（去重） */
    private Integer monthQuotedItems;
    /** 本月提交的有货报价条数 */
    private Integer monthQuotes;
    /** 本月提交的无货记录条数 */
    private Integer monthNoStock;
    /** 本月提交回价的任务数 */
    private Integer monthCompletedTasks;
    /** 本月平均回价用时（小时，分配到提交，保留 1 位小数，HALF_UP）；本月没有提交为空 */
    private BigDecimal monthAvgHours;
    /** 累计回价的型号数（去重） */
    private Integer totalQuotedItems;
    /** 近 30 天每天回价的型号数，含 0 */
    private List<DayCount> trend;
    /** 待处理任务前 5 条，排序同我的询价任务 */
    private List<MyTaskVO> todo;

    @Data
    public static class DayCount {
        private LocalDate date;
        private Integer items;
    }
}
