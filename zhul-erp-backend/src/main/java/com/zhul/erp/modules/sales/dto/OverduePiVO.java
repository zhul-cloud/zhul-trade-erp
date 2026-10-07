package com.zhul.erp.modules.sales.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** 工作台「过期未收款的 PI」卡片 */
@Data
public class OverduePiVO {
    private Long count;
    /** 按币种的合计 */
    private List<CurrencyTotal> totals;
    /** 过期天数最多的 5 张 */
    private List<PiListVO> top;

    @Data
    public static class CurrencyTotal {
        private String currencyCode;
        private BigDecimal amount;
    }
}
