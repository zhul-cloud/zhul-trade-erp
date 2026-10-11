package com.zhul.erp.modules.finance.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 付款记录统计：本月（或筛选范围内）付款人民币合计 */
@Data
public class PaymentStatsVO {
    private BigDecimal total = BigDecimal.ZERO;
    private BigDecimal company = BigDecimal.ZERO;
    private BigDecimal advance = BigDecimal.ZERO;
    private long count;
}
