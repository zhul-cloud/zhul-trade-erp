package com.zhul.erp.modules.quotation.support;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 生效中的定价策略（毛利率为百分数） */
public record PricingStrategy(Map<Integer, Margin> conditions, List<Tier> tiers, boolean hintDiscontinuedUrgent,
                              boolean hintPremiumBrand, boolean hintReturningCustomer, boolean hintToConfirm,
                              List<String> premiumBrands) {

    /** 建议毛利率与红线；marginRate 为空表示该品相不给建议值 */
    public record Margin(BigDecimal marginRate, BigDecimal floorRate) {
    }

    /** 采购成本价 ≤ maxCost（CNY）时用 marginRate */
    public record Tier(BigDecimal maxCost, BigDecimal marginRate) {
    }

    public Set<Integer> configuredConditions() {
        return conditions.keySet();
    }
}
