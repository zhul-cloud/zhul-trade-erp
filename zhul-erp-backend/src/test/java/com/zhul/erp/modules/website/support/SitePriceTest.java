package com.zhul.erp.modules.website.support;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** spec website-publish「站点商品」售价四级来源；HALF_UP 保留 2 位 */
class SitePriceTest {

    private static final BigDecimal FACTOR = new BigDecimal("1.05");
    private static final SitePrice.Rate USD = new SitePrice.Rate(new BigDecimal("7.100000"), LocalDate.of(2026, 11, 27));
    private static final SitePrice.Quote Q760 = new SitePrice.Quote(9L, new BigDecimal("760.00"), LocalDateTime.of(2026, 11, 1, 10, 0));

    @Test
    void lowestQuote_specExample_112_39() {
        SitePrice.Result r = SitePrice.calc(null, Q760, FACTOR, USD, null);
        assertEquals(new BigDecimal("112.39"), r.amount(), "760 × 1.05 ÷ 7.1 = 112.394…");
        assertEquals(SitePrice.SOURCE_QUOTE, r.source());
        assertEquals(9L, r.quoteId());
        assertEquals(new BigDecimal("760.00"), r.unitPriceCny());
        assertEquals(FACTOR, r.factor());
        assertEquals(USD.rate(), r.rate());
        assertEquals(USD.rateDate(), r.rateDate());
    }

    @Test
    void halfUpAtTheEnd() {
        assertEquals(new BigDecimal("15.00"), SitePrice.calc(null, quote("100"), FACTOR, rate("7"), null).amount(), "100 × 1.05 ÷ 7 = 15");
        assertEquals(new BigDecimal("1.48"), SitePrice.calc(null, quote("10"), FACTOR, USD, null).amount(), "1.47887… 进位");
        assertEquals(new BigDecimal("0.01"), SitePrice.calc(null, quote("0.005"), BigDecimal.ONE, rate("1"), null).amount(), "正好 .005 进位");
    }

    @Test
    void manualWinsOverEverything() {
        SitePrice.Result r = SitePrice.calc(new BigDecimal("99.999"), Q760, FACTOR, USD, ref("120", "USD", null));
        assertEquals(new BigDecimal("100.00"), r.amount());
        assertEquals(SitePrice.SOURCE_MANUAL, r.source());
    }

    @Test
    void zeroValues_areIgnored() {
        assertEquals(SitePrice.SOURCE_QUOTE, SitePrice.calc(BigDecimal.ZERO, Q760, FACTOR, USD, null).source(), "手工 0 视为没填");
        assertNull(SitePrice.calc(null, quote("0"), FACTOR, USD, null).amount(), "单价 0 不算");
        assertNull(SitePrice.calc(null, quote("0.001"), FACTOR, USD, null).amount(), "舍入后为 0 也不算");
        assertNull(SitePrice.calc(null, null, FACTOR, USD, ref("0", "USD", null)).amount());
    }

    @Test
    void noQuote_usesReferencePrice() {
        SitePrice.Result r = SitePrice.calc(null, null, FACTOR, USD, ref("215.5", "USD", null));
        assertEquals(new BigDecimal("215.50"), r.amount());
        assertEquals(SitePrice.SOURCE_REFERENCE, r.source());
        // 人民币参考价按美元汇率换算：710 ÷ 7.1 = 100.00
        assertEquals(new BigDecimal("100.00"), SitePrice.calc(null, null, FACTOR, USD, ref("710", "CNY", "710")).amount());
    }

    @Test
    void noRate_quoteSkipped_fallsBackToUsdReference_elseEmpty() {
        assertEquals(new BigDecimal("80.00"), SitePrice.calc(null, Q760, FACTOR, null, ref("80", "USD", null)).amount());
        assertNull(SitePrice.calc(null, Q760, FACTOR, null, ref("710", "CNY", "710")).amount(), "人民币参考价也要汇率");
        assertEquals(SitePrice.Result.NONE, SitePrice.calc(null, Q760, FACTOR, null, null));
    }

    @Test
    void nothing_isEmpty_andLargeAmountsKeepPrecision() {
        assertEquals(SitePrice.Result.NONE, SitePrice.calc(null, null, FACTOR, USD, null));
        assertEquals(new BigDecimal("147887323943.66"),
                SitePrice.calc(null, quote("999999999999.99"), FACTOR, USD, null).amount());
    }

    private static SitePrice.Quote quote(String cny) {
        return new SitePrice.Quote(1L, new BigDecimal(cny), LocalDateTime.of(2026, 11, 1, 0, 0));
    }

    private static SitePrice.Rate rate(String r) {
        return new SitePrice.Rate(new BigDecimal(r), LocalDate.of(2026, 11, 27));
    }

    private static SitePrice.Reference ref(String original, String currency, String cny) {
        return new SitePrice.Reference(new BigDecimal(original), currency, cny == null ? null : new BigDecimal(cny));
    }
}
