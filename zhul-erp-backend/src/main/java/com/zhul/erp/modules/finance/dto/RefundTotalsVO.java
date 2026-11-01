package com.zhul.erp.modules.finance.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 退款单按类型汇总（人民币）：售后退款（其中转客户余额）、余额提现 */
@Data
public class RefundTotalsVO {
    private BigDecimal afterSales;
    private BigDecimal toCredit;
    private Integer afterSalesCount;
    private BigDecimal withdraw;
    private Integer withdrawCount;
}
