package com.zhul.erp.modules.quotation.support;

import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.quotation.constants.QuotationConstants;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 报价计算（SOP V6，按售价计算的毛利率）：
 * <ul>
 *   <li>建议毛利率：有明确品相（含国产替代）用该品相的建议值；否则采购成本价落在低值耗材分层内用分层；否则品相待查、不给建议值</li>
 *   <li>定价方式：按毛利率 售价(CNY)=采购成本价÷(1−毛利率)；按加价 售价(CNY)=采购成本价+加价；直接填外币售价 售价(CNY)=外币售价×汇率</li>
 *   <li>外币售价=售价(CNY)÷汇率；毛利率=1−采购成本价÷(外币售价×汇率)；小计=外币售价×数量；
 *       净利润(外币)=小计−采购成本价×数量÷汇率；净利润(CNY)=小计×汇率−采购成本价×数量</li>
 * </ul>
 * 精度：中间过程保持完整精度（34 位有效数字），金额只在输出时舍入到 2 位、毛利率 2 位（展示时再取 1 位），均为 HALF_UP。
 */
public final class QuotationPricing {

    public static final int MODE_MARGIN = 1;
    public static final int MODE_MARKUP = 2;
    public static final int MODE_PRICE = 3;

    public static final String HINT_DISCONTINUED_URGENT = "DISCONTINUED_URGENT";
    public static final String HINT_PREMIUM_BRAND = "PREMIUM_BRAND";
    public static final String HINT_TO_CONFIRM = "TO_CONFIRM";

    private static final MathContext MC = MathContext.DECIMAL128;
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final int MONEY = 2;

    private QuotationPricing() {
    }

    /** 建议毛利率：marginRate 为空表示品相待查；floorRate 为空表示不标红 */
    public record Suggestion(BigDecimal marginRate, BigDecimal floorRate, String basis) {
        public boolean toConfirm() {
            return marginRate == null;
        }
    }

    public static Suggestion suggest(Integer itemCondition, BigDecimal costPrice, PricingStrategy strategy,
                                     Map<Integer, String> conditionNames) {
        int condition = itemCondition == null ? 0 : itemCondition;
        PricingStrategy.Margin m = strategy.conditions().get(condition);
        if (condition != QuotationConstants.CONDITION_TO_CONFIRM && m != null && m.marginRate() != null) {
            String name = conditionNames.getOrDefault(condition, "货况");
            return new Suggestion(m.marginRate(), m.floorRate(), name + " " + percent(m.marginRate()));
        }
        if (costPrice != null && costPrice.signum() >= 0) {
            for (PricingStrategy.Tier t : strategy.tiers()) {
                if (costPrice.compareTo(t.maxCost()) <= 0) {
                    return new Suggestion(t.marginRate(), null, "低值耗材 ≤ CNY " + t.maxCost().stripTrailingZeros().toPlainString());
                }
            }
        }
        return new Suggestion(null, null, "品相待查");
    }

    /** 一行的定价输入 */
    public record Input(BigDecimal costPrice, int quantity, BigDecimal exchangeRate, int mode,
                        BigDecimal marginRate, BigDecimal markupAmount, BigDecimal unitPrice) {
    }

    /** 一行的计算结果；没有采购成本价时毛利率、净利润为空；售价为 0 时毛利率为空 */
    public record Result(BigDecimal costPriceForeign, BigDecimal unitPriceCny, BigDecimal unitPrice, BigDecimal marginRate,
                         BigDecimal amount, BigDecimal amountCny, BigDecimal netProfit, BigDecimal netProfitCny) {
    }

    public static Result calculate(Input in) {
        BigDecimal rate = in.exchangeRate();
        if (rate == null || rate.signum() <= 0) {
            throw new BizException("汇率需要大于 0");
        }
        if (in.quantity() <= 0) {
            throw new BizException("数量需要大于 0");
        }
        BigDecimal cost = in.costPrice();
        int mode = cost == null ? MODE_PRICE : in.mode();
        BigDecimal priceCny;
        BigDecimal unitPrice;
        switch (mode) {
            case MODE_MARGIN -> {
                BigDecimal m = in.marginRate();
                if (m == null) {
                    throw new BizException("请填写毛利率");
                }
                if (m.signum() < 0 || m.compareTo(HUNDRED) >= 0) {
                    throw new BizException("毛利率需要在 0–100% 之间（不含 100%）");
                }
                priceCny = cost.divide(BigDecimal.ONE.subtract(m.divide(HUNDRED, MC)), MC);
                unitPrice = priceCny.divide(rate, MC).setScale(MONEY, RoundingMode.HALF_UP);
            }
            case MODE_MARKUP -> {
                BigDecimal markup = in.markupAmount();
                if (markup == null || markup.signum() < 0) {
                    throw new BizException("加价金额不能为负");
                }
                priceCny = cost.add(markup);
                unitPrice = priceCny.divide(rate, MC).setScale(MONEY, RoundingMode.HALF_UP);
            }
            case MODE_PRICE -> {
                BigDecimal p = in.unitPrice();
                if (p == null || p.signum() < 0) {
                    throw new BizException("请填写售价");
                }
                unitPrice = p.setScale(MONEY, RoundingMode.HALF_UP);
                priceCny = unitPrice.multiply(rate, MC);
            }
            default -> throw new BizException("定价方式不正确");
        }
        BigDecimal qty = BigDecimal.valueOf(in.quantity());
        BigDecimal amount = unitPrice.multiply(qty);
        BigDecimal amountCnyExact = amount.multiply(rate, MC);
        BigDecimal margin = null;
        BigDecimal netProfit = null;
        BigDecimal netProfitCny = null;
        BigDecimal costForeign = null;
        if (cost != null) {
            costForeign = cost.divide(rate, MC).setScale(MONEY, RoundingMode.HALF_UP);
            BigDecimal totalCost = cost.multiply(qty);
            netProfit = amount.subtract(totalCost.divide(rate, MC)).setScale(MONEY, RoundingMode.HALF_UP);
            netProfitCny = amountCnyExact.subtract(totalCost).setScale(MONEY, RoundingMode.HALF_UP);
            if (unitPrice.signum() > 0) {
                margin = mode == MODE_MARGIN ? in.marginRate().setScale(MONEY, RoundingMode.HALF_UP)
                        : BigDecimal.ONE.subtract(cost.divide(unitPrice.multiply(rate, MC), MC)).multiply(HUNDRED)
                        .setScale(MONEY, RoundingMode.HALF_UP);
            }
        }
        return new Result(costForeign, priceCny.setScale(MONEY, RoundingMode.HALF_UP), unitPrice, margin,
                amount.setScale(MONEY, RoundingMode.HALF_UP), amountCnyExact.setScale(MONEY, RoundingMode.HALF_UP),
                netProfit, netProfitCny);
    }

    /** 建议主管核价提示码（只提示，不拦截） */
    public static List<String> hints(PricingStrategy strategy, Suggestion suggestion, Integer lifecycle, boolean urgent,
                                     boolean premiumBrand) {
        List<String> hints = new ArrayList<>(3);
        int life = lifecycle == null ? 0 : lifecycle;
        if (strategy.hintDiscontinuedUrgent() && urgent && life == QuotationConstants.LIFECYCLE_DISCONTINUED) {
            hints.add(HINT_DISCONTINUED_URGENT);
        }
        if (strategy.hintPremiumBrand() && premiumBrand) {
            hints.add(HINT_PREMIUM_BRAND);
        }
        if (strategy.hintToConfirm() && (suggestion.toConfirm() || life == QuotationConstants.LIFECYCLE_TO_CONFIRM)) {
            hints.add(HINT_TO_CONFIRM);
        }
        return hints;
    }

    /** 毛利率低于红线（毛利率为空或没有红线时不算） */
    public static boolean belowFloor(BigDecimal marginRate, BigDecimal floorRate) {
        return marginRate != null && floorRate != null && marginRate.compareTo(floorRate) < 0;
    }

    private static String percent(BigDecimal v) {
        return v.stripTrailingZeros().toPlainString() + "%";
    }
}
