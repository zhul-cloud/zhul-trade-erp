package com.zhul.erp.modules.quotation.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 报价策略「按金额分层毛利」的一档：采购成本价（CNY）≤ maxCost 时用 marginRate（%）；maxCost 为空表示以上全部 */
@Data
public class StrategyTierDTO {
    private BigDecimal maxCost;
    private BigDecimal marginRate;
}
