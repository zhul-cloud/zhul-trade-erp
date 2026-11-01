package com.zhul.erp.modules.finance.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** 余额管理顶部汇总：各币种可用余额、被 PI 占用、本月转给客户 */
@Data
public class CreditSummaryVO {
    private List<CurrencyTotal> currencies;
    private List<CurrencyTotal> occupied;
    private BigDecimal monthWithdrawCny;
    private Integer monthWithdrawCount;
    private BigDecimal monthFxGain;

    @Data
    public static class CurrencyTotal {
        private String currencyCode;
        private BigDecimal amount;
        private BigDecimal amountCny;
        /** 客户数或 PI 数 */
        private Integer count;
    }
}
