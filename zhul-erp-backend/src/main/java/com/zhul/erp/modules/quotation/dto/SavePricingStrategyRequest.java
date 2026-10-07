package com.zhul.erp.modules.quotation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 保存定价策略：整体提交 */
@Data
public class SavePricingStrategyRequest {
    @NotNull(message = "请填写品相毛利率")
    @Valid
    private List<PricingStrategyVO.ConditionMargin> conditions;
    @NotNull(message = "请填写低值耗材分层")
    @Size(max = 10, message = "低值耗材分层最多 10 档")
    private List<PricingStrategyVO.AmountTier> tiers;
    @NotNull(message = "请设置转人工提示")
    private PricingStrategyVO.Hints hints;
    @NotNull(message = "请填写现货优势品牌")
    @Size(max = 100, message = "现货优势品牌最多 100 个")
    private List<String> premiumBrands;
}
