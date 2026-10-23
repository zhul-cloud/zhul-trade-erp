package com.zhul.erp.modules.finance.support;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 公共费用按销售额比例分摊（spec system/business-unit） */
class BuShareCalcTest {

    private static final BigDecimal MIN = new BigDecimal("0.20");

    private static Map<Integer, BigDecimal> rev(Object... kv) {
        Map<Integer, BigDecimal> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((Integer) kv[i], new BigDecimal(kv[i + 1].toString()));
        }
        return m;
    }

    private static String pct(BigDecimal share) {
        return BuShareCalc.display(share).toPlainString();
    }

    @Test
    void newUnitBelowMinimumBearsMinimum() {
        Map<Integer, BigDecimal> s = BuShareCalc.shares(rev(1, "825000", 2, "200000"), MIN);
        assertEquals("0.8000", pct(s.get(1)));
        assertEquals("0.2000", pct(s.get(2)));
        Map<Integer, BigDecimal> money = BuShareCalc.split(new BigDecimal("26990.00"), s);
        assertEquals(new BigDecimal("21592.00"), money.get(1));
        assertEquals(new BigDecimal("5398.00"), money.get(2));
    }

    @Test
    void aboveMinimumUsesSalesRatio() {
        Map<Integer, BigDecimal> s = BuShareCalc.shares(rev(1, "600000", 2, "400000"), MIN);
        assertEquals("0.6000", pct(s.get(1)));
        assertEquals("0.4000", pct(s.get(2)));
    }

    @Test
    void zeroSalesUnitStillBearsMinimum() {
        Map<Integer, BigDecimal> s = BuShareCalc.shares(rev(1, "500000", 2, "0"), MIN);
        assertEquals("0.8000", pct(s.get(1)));
        assertEquals("0.2000", pct(s.get(2)));
    }

    @Test
    void noSalesSplitsEqually() {
        Map<Integer, BigDecimal> s = BuShareCalc.shares(rev(1, "0", 2, "0"), MIN);
        assertEquals("0.5000", pct(s.get(1)));
        assertEquals("0.5000", pct(s.get(2)));
    }

    @Test
    void singleUnitBearsAll() {
        Map<Integer, BigDecimal> s = BuShareCalc.shares(rev(1, "0"), MIN);
        assertEquals(0, BigDecimal.ONE.compareTo(s.get(1)));
    }

    @Test
    void minimumTimesCountOverHundredSplitsEqually() {
        Map<Integer, BigDecimal> s = BuShareCalc.shares(rev(1, "900000", 2, "50000", 3, "50000"), new BigDecimal("0.40"));
        assertEquals("0.3333", pct(s.get(1)));
        assertEquals("0.3333", pct(s.get(3)));
    }

    @Test
    void cascadingMinimumAcrossThreeUnits() {
        // 第一轮：2 号 8.33%、3 号 8.33% 都低于 20% → 固定；剩余 60% 全给 1 号
        Map<Integer, BigDecimal> s = BuShareCalc.shares(rev(1, "1000000", 2, "100000", 3, "100000"), MIN);
        assertEquals("0.6000", pct(s.get(1)));
        assertEquals("0.2000", pct(s.get(2)));
        assertEquals("0.2000", pct(s.get(3)));
        BigDecimal sum = s.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, BigDecimal.ONE.compareTo(sum.setScale(10, java.math.RoundingMode.HALF_UP)));
    }

    @Test
    void roundingRemainderGoesToLargestShare() {
        Map<Integer, BigDecimal> s = BuShareCalc.shares(rev(1, "1", 2, "1", 3, "1"), BigDecimal.ZERO);
        Map<Integer, BigDecimal> money = BuShareCalc.split(new BigDecimal("100.00"), s);
        BigDecimal sum = money.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(new BigDecimal("100.00"), sum);
        assertEquals(new BigDecimal("33.34"), money.get(1));
        assertTrue(money.get(2).compareTo(new BigDecimal("33.33")) == 0);
    }

    @Test
    void negativeRevenueCountsAsZero() {
        Map<Integer, BigDecimal> s = BuShareCalc.shares(rev(1, "-5000", 2, "100000"), MIN);
        assertEquals("0.2000", pct(s.get(1)));
        assertEquals("0.8000", pct(s.get(2)));
    }
}
