package com.zhul.erp.modules.finance.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 付款单统计：本月（或筛选范围内）付款人民币合计 */
@Data
public class PaymentStatsVO {
    private BigDecimal total = BigDecimal.ZERO;
    private BigDecimal company = BigDecimal.ZERO;
    private BigDecimal advance = BigDecimal.ZERO;
    /** 私对私 */
    private BigDecimal personal = BigDecimal.ZERO;
    /** 报销付款（公司付给员工，不计入上面的合计） */
    private BigDecimal reimburse = BigDecimal.ZERO;
    private long count;
}
