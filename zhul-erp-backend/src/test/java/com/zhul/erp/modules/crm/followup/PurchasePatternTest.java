package com.zhul.erp.modules.crm.followup;

import com.zhul.erp.modules.crm.followup.support.PurchasePattern;
import com.zhul.erp.modules.crm.followup.support.PurchasePattern.Line;
import com.zhul.erp.modules.crm.followup.support.PurchasePattern.Order;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 采购规律（第三期 design D3）：合并 7 天内的订单、看近 3 年、按次数给说明、中位数周期、近 12 个月品牌品类。
 */
class PurchasePatternTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 5);

    private static Order order(LocalDate d) {
        return new Order(d, List.of());
    }

    private static Order order(LocalDate d, Line... lines) {
        return new Order(d, List.of(lines));
    }

    private static Line line(String brand, String category, int amount) {
        return new Line(brand, category, BigDecimal.valueOf(amount));
    }

    @Test
    void noOrders() {
        PurchasePattern.Result r = PurchasePattern.of(List.of(), TODAY);
        assertEquals(0, r.purchases());
        assertEquals("近 3 年没有订单", r.text());
        assertNull(r.nextDate());
        assertEquals("", r.brands());
    }

    @Test
    void ordersOlderThanThreeYearsAreIgnored() {
        PurchasePattern.Result r = PurchasePattern.of(List.of(order(TODAY.minusYears(3).minusDays(1))), TODAY);
        assertEquals("近 3 年没有订单", r.text());
    }

    @Test
    void onePurchaseIsNotEnough() {
        PurchasePattern.Result r = PurchasePattern.of(List.of(order(LocalDate.of(2026, 3, 1))), TODAY);
        assertEquals(1, r.purchases());
        assertEquals("数据不足", r.text());
        assertEquals(0, r.cycleDays());
        assertNull(r.nextDate());
    }

    @Test
    void twoPurchasesShowGapOnly() {
        PurchasePattern.Result r = PurchasePattern.of(List.of(order(LocalDate.of(2026, 1, 10)), order(LocalDate.of(2026, 4, 10))), TODAY);
        assertEquals(2, r.purchases());
        assertEquals("样本少：两次相隔 90 天，仅供参考", r.text());
        assertNull(r.nextDate(), "两次不推测下次采购日");
    }

    @Test
    void ordersWithinSevenDaysMergeIntoOnePurchase() {
        // 1/1、1/5、1/11 连续相隔不超过 7 天，算一次（采购日取 1/1）；间隔 90、91，偶数个取平均向下取整 = 90
        PurchasePattern.Result r = PurchasePattern.of(List.of(order(LocalDate.of(2026, 1, 11)), order(LocalDate.of(2026, 1, 1)),
                order(LocalDate.of(2026, 1, 5)), order(LocalDate.of(2026, 4, 1)), order(LocalDate.of(2026, 7, 1))), TODAY);
        assertEquals(3, r.purchases());
        assertEquals(90, r.cycleDays());
        assertEquals(LocalDate.of(2026, 7, 1).plusDays(90), r.nextDate());
        assertEquals("约每 90 天一次，下次约 9 月", r.text());
    }

    @Test
    void cycleIsMedianOfGaps() {
        // 间隔 60、120、60 → 中位数 60，不受 120 这一次拉长
        LocalDate d1 = LocalDate.of(2025, 1, 1);
        LocalDate d2 = d1.plusDays(60);
        LocalDate d3 = d2.plusDays(120);
        LocalDate d4 = d3.plusDays(60);
        PurchasePattern.Result r = PurchasePattern.of(List.of(order(d1), order(d2), order(d3), order(d4)), TODAY);
        assertEquals(4, r.purchases());
        assertEquals(60, r.cycleDays());
        assertEquals(d4.plusDays(60), r.nextDate());
        assertEquals("约每 60 天一次，下次约 " + d4.plusDays(60).getMonthValue() + " 月", r.text());
    }

    @Test
    void brandsAreTopThreeByAmountInLastTwelveMonths() {
        PurchasePattern.Result r = PurchasePattern.of(List.of(
                order(LocalDate.of(2026, 9, 1), line("Siemens", "PLC", 100), line("ABB", "变频器", 300), line("Omron", "HMI", 50)),
                order(LocalDate.of(2026, 10, 1), line("Siemens", "PLC", 250), line("Delta", "伺服", 10), line(" ", null, 999)),
                order(TODAY.minusMonths(13), line("Old", "旧品类", 100000))), TODAY);
        assertEquals("Siemens、ABB、Omron · PLC、变频器、HMI", r.brands());
    }

    @Test
    void brandsWithoutCategoryHaveNoSeparator() {
        PurchasePattern.Result r = PurchasePattern.of(List.of(order(LocalDate.of(2026, 9, 1), line("Siemens", null, 100))), TODAY);
        assertEquals("Siemens", r.brands());
    }
}
