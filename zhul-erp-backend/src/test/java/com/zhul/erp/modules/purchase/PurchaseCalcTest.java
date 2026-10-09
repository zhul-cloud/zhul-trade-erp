package com.zhul.erp.modules.purchase;

import com.zhul.erp.modules.purchase.support.PurchaseCalc;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PurchaseCalcTest {

    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    @Test
    void taxIncludedPriceComparesWithTaxExclusiveTarget() {
        assertEquals(d("800.00"), PurchaseCalc.netPrice(d("904.00"), BigDecimal.ONE, true, d("13")));
        assertEquals(d("12000.00"), PurchaseCalc.bargain(d("1000.00"), d("904.00"), 60, BigDecimal.ONE, true, d("13")));
    }

    @Test
    void priceRiseIsNegative() {
        BigDecimal b = PurchaseCalc.bargain(d("500.00"), d("587.60"), 10, BigDecimal.ONE, true, d("13"));
        assertEquals(d("-200.00"), b);
        assertEquals(d("-4.00"), PurchaseCalc.rate(b, d("5000.00")));
    }

    @Test
    void weightedRate() {
        assertEquals(d("18.15"), PurchaseCalc.rate(d("11800.00"), d("65000.00")));
    }

    @Test
    void noTargetOrNoPrice() {
        assertNull(PurchaseCalc.bargain(null, d("100"), 1, BigDecimal.ONE, false, BigDecimal.ZERO));
        assertNull(PurchaseCalc.bargain(d("100"), null, 1, BigDecimal.ONE, false, BigDecimal.ZERO));
        assertNull(PurchaseCalc.rate(d("10"), BigDecimal.ZERO));
    }

    @Test
    void zeroPriceIsFullBargain() {
        assertEquals(d("200.00"), PurchaseCalc.bargain(d("100.00"), BigDecimal.ZERO, 2, BigDecimal.ONE, false, BigDecimal.ZERO));
    }

    @Test
    void foreignCurrencyConvertsBeforeTax() {
        // USD 100 × 7.1 ÷ 1.13 = 628.3185… → 628.32；目标价 700 × 3 = 2100 − 1884.955… = 215.04
        assertEquals(d("628.32"), PurchaseCalc.netPrice(d("100"), d("7.1"), true, d("13")));
        assertEquals(d("215.04"), PurchaseCalc.bargain(d("700"), d("100"), 3, d("7.1"), true, d("13")));
    }

    @Test
    void largeAmountsKeepPrecision() {
        assertEquals(d("99999999.00"), PurchaseCalc.bargain(d("1000000.00"), d("0.01"), 100, BigDecimal.ONE, false, BigDecimal.ZERO));
    }
}
