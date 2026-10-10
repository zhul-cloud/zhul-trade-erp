package com.zhul.erp.modules.sales;

import com.zhul.erp.modules.sales.support.PiBargain;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 议价测算：与规格 sales/proforma-invoice「议价测算」场景、前端 bargain.test.ts 同一组数字 */
class PiBargainTest {

    private static final BigDecimal RATE = new BigDecimal("6.65");

    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    /** 小计 USD 1,000、成本 CNY 5,400、红线 10% */
    private static PiBargain.Summary spec() {
        return PiBargain.summarize(List.of(
                new PiBargain.Line(2, d("1500"), d("600"), d("10")),
                new PiBargain.Line(1, d("2400"), d("400"), d("10"))));
    }

    @Test
    void maxExtraDiscount_specScenario() {
        PiBargain.Summary s = spec();
        assertThat(s.costTotal()).isEqualByComparingTo("5400");
        assertThat(PiBargain.marginAfter(s, BigDecimal.ZERO, RATE).setScale(1, RoundingMode.HALF_UP)).isEqualByComparingTo("18.8");
        BigDecimal max = PiBargain.maxExtraDiscount(s, BigDecimal.ZERO, RATE);
        assertThat(max).isEqualByComparingTo("97.74");
        assertThat(PiBargain.points(max, s.itemAmount())).isEqualByComparingTo("9.7");
        // 按上限让利后不低于红线
        assertThat(PiBargain.marginAfter(s, max, RATE)).isGreaterThanOrEqualTo(d("10"));
        assertThat(PiBargain.revenueCnyAfter(s, max, RATE).setScale(2, RoundingMode.HALF_UP)).isEqualByComparingTo("6000.03");
    }

    @Test
    void trialByTargetAndByPoints() {
        PiBargain.Summary s = spec();
        // 目标总价 950 → 折扣 50 → 14.5%，比红线高 4.5 点
        assertThat(PiBargain.marginAfter(s, d("50"), RATE).setScale(1, RoundingMode.HALF_UP)).isEqualByComparingTo("14.5");
        // 12 点 → 折扣 120 → 7.7%，低于红线 2.3 点
        BigDecimal m = PiBargain.marginAfter(s, d("120"), RATE);
        assertThat(d("10").subtract(m).setScale(1, RoundingMode.HALF_UP)).isEqualByComparingTo("2.3");
        // 已有折扣时「还能再让」扣掉当前折扣
        assertThat(PiBargain.maxExtraDiscount(s, d("50"), RATE)).isEqualByComparingTo("47.74");
        assertThat(PiBargain.maxExtraDiscount(s, d("200"), RATE)).isEqualByComparingTo("0.00");
    }

    @Test
    void weightedFloor_uncostedLinesExcluded_tempRate() {
        PiBargain.Summary s = PiBargain.summarize(List.of(
                new PiBargain.Line(1, d("3000"), d("600"), d("10")),
                new PiBargain.Line(1, d("2000"), d("400"), d("15")),
                new PiBargain.Line(1, null, d("100"), null)));
        assertThat(s.floorMargin()).isEqualByComparingTo("12");
        assertThat(s.costedRevenue()).isEqualByComparingTo("1000");
        assertThat(s.itemAmount()).isEqualByComparingTo("1100");
        // 临时汇率 6.60 时还能让的更少
        assertThat(PiBargain.maxExtraDiscount(s, BigDecimal.ZERO, d("6.60")))
                .isLessThan(PiBargain.maxExtraDiscount(s, BigDecimal.ZERO, RATE));
        PiBargain.Summary none = PiBargain.summarize(List.of(new PiBargain.Line(1, null, d("100"), null)));
        assertThat(none.costed()).isFalse();
        assertThat(PiBargain.maxExtraDiscount(none, BigDecimal.ZERO, RATE)).isNull();
    }
}
