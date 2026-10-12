package com.zhul.erp.modules.finance.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 应付单顶部统计（人民币，按应付汇率折算未付与欠款） */
@Data
public class PayableStatsVO {
    private BigDecimal monthDue = BigDecimal.ZERO;
    private long monthDueCount;
    private BigDecimal overdue = BigDecimal.ZERO;
    private long overdueCount;
    private BigDecimal soon = BigDecimal.ZERO;
    private long soonCount;
    private BigDecimal credit = BigDecimal.ZERO;
    private long creditCount;
}
