package com.zhul.erp.modules.inventory.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/** 成本调整列表顶部的月份合计（人民币） */
@Data
public class CostAdjustSummaryVO {
    private String month;
    private String currencyCode = "CNY";
    private BigDecimal stockAmount;
    private BigDecimal cogsAmount;
    private BigDecimal otherAmount;
    private Integer count;
    /** 触发原因名称 → 张数 */
    private Map<String, Integer> countByReason;
}
