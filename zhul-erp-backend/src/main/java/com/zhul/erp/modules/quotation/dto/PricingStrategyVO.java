package com.zhul.erp.modules.quotation.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** 定价策略（毛利率均为百分数，如 10.00 表示 10%） */
@Data
public class PricingStrategyVO {
    private List<ConditionMargin> conditions;
    private List<AmountTier> tiers;
    private Hints hints;
    private List<String> premiumBrands;

    @Data
    public static class ConditionMargin {
        private Integer itemCondition;
        private String conditionName;
        private BigDecimal marginRate;
        private BigDecimal floorRate;
        /** 字典新增的货况还没有设置毛利率 */
        private Boolean configured;
    }

    @Data
    public static class AmountTier {
        private BigDecimal maxCost;
        private BigDecimal marginRate;
    }

    @Data
    public static class Hints {
        private Boolean discontinuedUrgent;
        private Boolean premiumBrand;
        private Boolean returningCustomer;
        private Boolean toConfirm;
    }
}
