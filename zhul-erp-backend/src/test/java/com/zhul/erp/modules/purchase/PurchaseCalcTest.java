package com.zhul.erp.modules.purchase;

import com.zhul.erp.modules.purchase.support.PurchaseCalc;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** spec purchase/purchase-order「价格、开票与砍价」：人民币单价 = 单价 × 汇率，砍价与目标价直接比较；开票成本 = 开票金额 × 税点 */
class PurchaseCalcTest {

    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    @Test
    void bargainComparesPriceDirectly() {
        assertEquals(d("800.00"), PurchaseCalc.netPrice(d("800.00"), BigDecimal.ONE));
        assertEquals(d("12000.00"), PurchaseCalc.bargain(d("1000.00"), d("800.00"), 60, BigDecimal.ONE));
    }

    @Test
    void priceRiseIsNegative() {
        BigDecimal b = PurchaseCalc.bargain(d("500.00"), d("520.00"), 10, BigDecimal.ONE);
        assertEquals(d("-200.00"), b);
        assertEquals(d("-4.00"), PurchaseCalc.rate(b, d("5000.00")));
    }

    @Test
    void weightedRate() {
        assertEquals(d("18.15"), PurchaseCalc.rate(d("11800.00"), d("65000.00")));
    }

    @Test
    void noTargetOrNoPrice() {
        assertNull(PurchaseCalc.bargain(null, d("100"), 1, BigDecimal.ONE));
        assertNull(PurchaseCalc.bargain(d("100"), null, 1, BigDecimal.ONE));
        assertNull(PurchaseCalc.rate(d("10"), BigDecimal.ZERO));
    }

    @Test
    void zeroPriceIsFullBargain() {
        assertEquals(d("200.00"), PurchaseCalc.bargain(d("100.00"), BigDecimal.ZERO, 2, BigDecimal.ONE));
    }

    @Test
    void foreignCurrencyConverts() {
        // USD 100 × 7.1 = 710；目标价 720 × 3 = 2160 − 2130 = 30
        assertEquals(d("710.00"), PurchaseCalc.netPrice(d("100"), d("7.1")));
        assertEquals(d("30.00"), PurchaseCalc.bargain(d("720"), d("100"), 3, d("7.1")));
    }

    @Test
    void invoiceCost() {
        assertEquals(d("480.00"), PurchaseCalc.invoiceCost(d("16000.00"), d("3")));
        assertEquals(d("63.12"), PurchaseCalc.invoiceCost(d("6312.00"), d("1")));
        assertEquals(d("0.00"), PurchaseCalc.invoiceCost(d("16000.00"), BigDecimal.ZERO));
        // 2.03% × 26,767 = 543.3701 → 543.37
        assertEquals(d("543.37"), PurchaseCalc.invoiceCost(d("26767.00"), d("2.03")));
    }

    @Test
    void largeAmountsKeepPrecision() {
        assertEquals(d("99999999.00"), PurchaseCalc.bargain(d("1000000.00"), d("0.01"), 100, BigDecimal.ONE));
    }
}
