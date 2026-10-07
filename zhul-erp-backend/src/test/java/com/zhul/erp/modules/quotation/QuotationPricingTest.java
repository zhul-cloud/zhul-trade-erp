package com.zhul.erp.modules.quotation;

import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.quotation.support.PricingStrategy;
import com.zhul.erp.modules.quotation.support.QuotationPricing;
import com.zhul.erp.modules.quotation.support.QuotationPricing.Input;
import com.zhul.erp.modules.quotation.support.QuotationPricing.Result;
import com.zhul.erp.modules.quotation.support.QuotationPricing.Suggestion;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** spec quotation/pricing-rule 的计算口径与决策顺序 */
class QuotationPricingTest {

    private static final BigDecimal USD = new BigDecimal("7.150000");
    private static final Map<Integer, String> NAMES = Map.of(1, "全新原装", 2, "99新", 3, "翻新", 4, "二手", 5, "拆机件", 6, "国产替代", 7, "待确认");

    private static PricingStrategy sop(boolean hintsOn) {
        Map<Integer, PricingStrategy.Margin> c = new LinkedHashMap<>();
        c.put(1, margin("10", "10"));
        c.put(2, margin("20", "15"));
        c.put(3, margin("20", "15"));
        c.put(4, margin("15", "10"));
        c.put(5, margin("15", "10"));
        c.put(6, margin("62", "62"));
        c.put(7, new PricingStrategy.Margin(null, null));
        List<PricingStrategy.Tier> tiers = List.of(tier("100", "50"), tier("200", "30"), tier("300", "20"));
        return new PricingStrategy(c, tiers, hintsOn, hintsOn, hintsOn, hintsOn, List.of("VEGA"));
    }

    private static PricingStrategy.Margin margin(String m, String f) {
        return new PricingStrategy.Margin(new BigDecimal(m), new BigDecimal(f));
    }

    private static PricingStrategy.Tier tier(String max, String m) {
        return new PricingStrategy.Tier(new BigDecimal(max), new BigDecimal(m));
    }

    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    private static Result byMargin(String cost, String margin, BigDecimal rate, int qty) {
        return QuotationPricing.calculate(new Input(d(cost), qty, rate, QuotationPricing.MODE_MARGIN, d(margin), null, null));
    }

    // ---------------------------------------------------------------- 三种定价方式

    @Test
    void pricingByMargin() {
        Result r = byMargin("2640", "10", USD, 1);
        assertThat(r.unitPriceCny()).isEqualByComparingTo("2933.33");
        assertThat(r.unitPrice()).isEqualByComparingTo("410.26");
        assertThat(r.netProfit()).isEqualByComparingTo("41.03");
        assertThat(r.netProfitCny()).isEqualByComparingTo("293.36");
        assertThat(r.marginRate()).isEqualByComparingTo("10");
        assertThat(r.costPriceForeign()).isEqualByComparingTo("369.23");
        assertThat(r.amount()).isEqualByComparingTo("410.26");
        assertThat(r.amountCny()).isEqualByComparingTo("2933.36");
    }

    @Test
    void pricingByMarkup() {
        Result r = QuotationPricing.calculate(new Input(d("200"), 1, USD, QuotationPricing.MODE_MARKUP, null, d("50"), null));
        assertThat(r.unitPriceCny()).isEqualByComparingTo("250.00");
        assertThat(r.unitPrice()).isEqualByComparingTo("34.97");
        assertThat(r.marginRate().setScale(1, java.math.RoundingMode.HALF_UP)).isEqualByComparingTo("20.0");
        assertThat(r.netProfit()).isEqualByComparingTo("7.00");
        assertThat(r.netProfitCny()).isEqualByComparingTo("50.04");
    }

    @Test
    void changingForeignPriceRecalculatesMargin() {
        Result r = QuotationPricing.calculate(new Input(d("2640"), 1, USD, QuotationPricing.MODE_PRICE, null, null, d("400")));
        assertThat(r.marginRate().setScale(1, java.math.RoundingMode.HALF_UP)).isEqualByComparingTo("7.7");
        assertThat(r.netProfit()).isEqualByComparingTo("30.77");
        assertThat(r.netProfitCny()).isEqualByComparingTo("220.00");
        assertThat(r.unitPriceCny()).isEqualByComparingTo("2860.00");
    }

    @Test
    void quantityMultipliesSubtotalAndProfit() {
        Result r = byMargin("2640", "10", USD, 3);
        assertThat(r.amount()).isEqualByComparingTo("1230.78");
        assertThat(r.netProfit()).isEqualByComparingTo("123.09");
        assertThat(r.netProfitCny()).isEqualByComparingTo("880.08");
    }

    // ---------------------------------------------------------------- 建议毛利率决策顺序

    @Test
    void domesticAlternativeIgnoresAmount() {
        Suggestion s = QuotationPricing.suggest(6, d("180"), sop(true), NAMES);
        assertThat(s.marginRate()).isEqualByComparingTo("62");
        assertThat(s.basis()).isEqualTo("国产替代 62%");
    }

    @Test
    void explicitConditionIgnoresAmount() {
        Suggestion s = QuotationPricing.suggest(3, d("85"), sop(true), NAMES);
        assertThat(s.marginRate()).isEqualByComparingTo("20");
        assertThat(s.floorRate()).isEqualByComparingTo("15");
        assertThat(s.basis()).isEqualTo("翻新 20%");
    }

    @Test
    void lowValueConsumablesUseTiers() {
        assertThat(QuotationPricing.suggest(7, d("85"), sop(true), NAMES).basis()).isEqualTo("低值耗材 ≤ CNY 100");
        assertThat(QuotationPricing.suggest(7, d("85"), sop(true), NAMES).marginRate()).isEqualByComparingTo("50");
        assertThat(QuotationPricing.suggest(0, d("100"), sop(true), NAMES).marginRate()).isEqualByComparingTo("50");
        assertThat(QuotationPricing.suggest(null, d("100.01"), sop(true), NAMES).marginRate()).isEqualByComparingTo("30");
        assertThat(QuotationPricing.suggest(7, d("300"), sop(true), NAMES).marginRate()).isEqualByComparingTo("20");
        assertThat(QuotationPricing.suggest(7, d("85"), sop(true), NAMES).floorRate()).isNull();
    }

    @Test
    void conditionToConfirmAboveTiersHasNoSuggestion() {
        Suggestion s = QuotationPricing.suggest(7, d("1200"), sop(true), NAMES);
        assertThat(s.toConfirm()).isTrue();
        assertThat(s.basis()).isEqualTo("品相待查");
        assertThat(QuotationPricing.hints(sop(true), s, 1, false, false)).containsExactly(QuotationPricing.HINT_TO_CONFIRM);
        assertThat(QuotationPricing.suggest(9, d("1200"), sop(true), NAMES).toConfirm()).as("字典新增、未设置毛利率的货况").isTrue();
    }

    // ---------------------------------------------------------------- 转人工提示与红线

    @Test
    void discontinuedUrgentHintStillSuggestsByCondition() {
        Suggestion s = QuotationPricing.suggest(1, d("2640"), sop(true), NAMES);
        assertThat(s.marginRate()).isEqualByComparingTo("10");
        assertThat(QuotationPricing.hints(sop(true), s, 2, true, false)).containsExactly(QuotationPricing.HINT_DISCONTINUED_URGENT);
        assertThat(QuotationPricing.hints(sop(true), s, 2, false, false)).as("不紧急不提示").isEmpty();
        assertThat(QuotationPricing.hints(sop(false), s, 2, true, true)).as("提示已停用").isEmpty();
    }

    @Test
    void premiumBrandAndLifecycleToConfirm() {
        Suggestion s = QuotationPricing.suggest(1, d("5000"), sop(true), NAMES);
        assertThat(QuotationPricing.hints(sop(true), s, 3, false, true))
                .containsExactly(QuotationPricing.HINT_PREMIUM_BRAND, QuotationPricing.HINT_TO_CONFIRM);
    }

    @Test
    void belowFloorOnlyFlags() {
        Result r = QuotationPricing.calculate(new Input(d("2640"), 1, USD, QuotationPricing.MODE_PRICE, null, null, d("400")));
        assertThat(QuotationPricing.belowFloor(r.marginRate(), d("10"))).isTrue();
        assertThat(QuotationPricing.belowFloor(d("10"), d("10"))).isFalse();
        assertThat(QuotationPricing.belowFloor(null, d("10"))).isFalse();
        assertThat(QuotationPricing.belowFloor(d("5"), null)).isFalse();
    }

    // ---------------------------------------------------------------- 边界

    @Test
    void zeroCost() {
        Result r = byMargin("0", "10", USD, 2);
        assertThat(r.unitPrice()).isEqualByComparingTo("0.00");
        assertThat(r.marginRate()).as("售价为 0 时毛利率为空").isNull();
        assertThat(r.netProfit()).isEqualByComparingTo("0.00");
        Result markup = QuotationPricing.calculate(new Input(d("0"), 1, USD, QuotationPricing.MODE_MARKUP, null, d("50"), null));
        assertThat(markup.marginRate()).isEqualByComparingTo("100.00");
    }

    @Test
    void negativeProfitWhenPriceBelowCost() {
        Result r = QuotationPricing.calculate(new Input(d("2640"), 2, USD, QuotationPricing.MODE_PRICE, null, null, d("300")));
        assertThat(r.marginRate()).isNegative();
        assertThat(r.netProfit()).isEqualByComparingTo("-138.46");
        assertThat(r.netProfitCny()).isEqualByComparingTo("-990.00");
    }

    @Test
    void cnyQuoteUsesRateOne() {
        Result r = byMargin("2640", "10", BigDecimal.ONE, 1);
        assertThat(r.unitPrice()).isEqualByComparingTo("2933.33");
        assertThat(r.unitPriceCny()).isEqualByComparingTo("2933.33");
        assertThat(r.netProfit()).isEqualByComparingTo(r.netProfitCny());
    }

    @Test
    void hugeAmountKeepsPrecision() {
        Result r = byMargin("9999999999.99", "10", USD, 9999);
        assertThat(r.unitPriceCny()).isEqualByComparingTo("11111111111.10");
        assertThat(r.unitPrice()).isEqualByComparingTo("1554001554.00");
        assertThat(r.amount()).isEqualByComparingTo("15538461538446.00");
    }

    @Test
    void multipleCurrencies() {
        Result eur = byMargin("2640", "10", d("7.820000"), 1);
        assertThat(eur.unitPrice()).isEqualByComparingTo("375.11");
        Result jpy = byMargin("2640", "10", d("0.047800"), 1);
        assertThat(jpy.unitPrice()).isEqualByComparingTo("61366.81");
        assertThat(jpy.costPriceForeign()).isEqualByComparingTo("55230.13");
    }

    @Test
    void noCostOnlyDirectPriceAndNoMargin() {
        Result r = QuotationPricing.calculate(new Input(null, 1, USD, QuotationPricing.MODE_MARGIN, d("10"), null, d("120")));
        assertThat(r.unitPrice()).isEqualByComparingTo("120.00");
        assertThat(r.marginRate()).isNull();
        assertThat(r.netProfit()).isNull();
        assertThat(r.costPriceForeign()).isNull();
    }

    @Test
    void illegalInputs() {
        assertThatThrownBy(() -> byMargin("100", "100", USD, 1)).isInstanceOf(BizException.class).hasMessageContaining("毛利率");
        assertThatThrownBy(() -> byMargin("100", "-1", USD, 1)).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> byMargin("100", "10", USD, 0)).hasMessageContaining("数量");
        assertThatThrownBy(() -> byMargin("100", "10", BigDecimal.ZERO, 1)).hasMessageContaining("汇率");
        assertThatThrownBy(() -> QuotationPricing.calculate(new Input(d("100"), 1, USD, QuotationPricing.MODE_MARKUP, null, d("-1"), null)))
                .hasMessageContaining("加价");
        assertThatThrownBy(() -> QuotationPricing.calculate(new Input(d("100"), 1, USD, 9, null, null, null))).hasMessageContaining("定价方式");
    }
}
