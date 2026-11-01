package com.zhul.erp.modules.sales;

import com.zhul.erp.modules.sales.entity.SalesOrderDO;
import com.zhul.erp.modules.sales.entity.SalesOrderItemDO;
import com.zhul.erp.modules.sales.support.OrderTotals;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** 部分取消后的金额重算（spec sales/sales-order「按型号部分取消」） */
class OrderTotalsTest {

    private static SalesOrderItemDO item(int qty, String price, String cost, boolean profit) {
        SalesOrderItemDO i = new SalesOrderItemDO();
        i.setQuantity(qty);
        i.setUnitPrice(new BigDecimal(price));
        i.setCostPrice(cost == null ? null : new BigDecimal(cost));
        i.setNetProfit(profit ? BigDecimal.ONE : null);
        i.setNetProfitCny(profit ? BigDecimal.ONE : null);
        return i;
    }

    private static SalesOrderDO order(String rate, String fee, String discount, boolean profit) {
        SalesOrderDO o = new SalesOrderDO();
        o.setExchangeRate(new BigDecimal(rate));
        o.setFeeAmount(fee == null ? null : new BigDecimal(fee));
        o.setDiscountAmount(discount == null ? null : new BigDecimal(discount));
        o.setNetProfit(profit ? BigDecimal.ONE : null);
        return o;
    }

    private static void eq(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "期望 " + expected + "，实际 " + actual);
    }

    @Test
    void line_remainingQuantity_andZero() {
        SalesOrderItemDO a = item(5, "50.00", "200", true);
        OrderTotals.applyLine(a, new BigDecimal("7.15"));
        eq("250.00", a.getAmount());
        eq("1787.50", a.getAmountCny());
        eq("110.14", a.getNetProfit());
        eq("787.50", a.getNetProfitCny());

        SalesOrderItemDO zero = item(0, "50.00", "200", true);
        OrderTotals.applyLine(zero, new BigDecimal("7.15"));
        eq("0", zero.getAmount());
        eq("0", zero.getAmountCny());
        eq("0", zero.getNetProfit());

        SalesOrderItemDO manual = item(3, "99.99", null, false);
        OrderTotals.applyLine(manual, new BigDecimal("7.80"));
        eq("299.97", manual.getAmount());
        eq("2339.77", manual.getAmountCny());
        assertNull(manual.getNetProfit(), "手动订单的行没有净利润，保持为空");

        SalesOrderItemDO zeroManual = item(0, "10.00", null, false);
        OrderTotals.applyLine(zeroManual, BigDecimal.ONE);
        assertNull(zeroManual.getNetProfit());
    }

    @Test
    void discount_percentRecalculated_amountKept_cappedAtSubtotal() {
        eq("75.00", OrderTotals.discount(1, new BigDecimal("10"), new BigDecimal("100.00"), new BigDecimal("750.00")));
        eq("0.33", OrderTotals.discount(1, new BigDecimal("3.3"), BigDecimal.ZERO, new BigDecimal("10.00")), "HALF_UP");
        eq("300.00", OrderTotals.discount(2, new BigDecimal("300"), new BigDecimal("300.00"), new BigDecimal("450.00")));
        eq("200.00", OrderTotals.discount(2, new BigDecimal("300"), new BigDecimal("300.00"), new BigDecimal("200.00")));
        eq("0.00", OrderTotals.discount(null, null, null, new BigDecimal("100.00")));
        eq("0.00", OrderTotals.discount(0, null, BigDecimal.ZERO, BigDecimal.ZERO));
        eq("0.00", OrderTotals.discount(1, null, null, new BigDecimal("100.00")));
    }

    private static void eq(String expected, BigDecimal actual, String note) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), note);
    }

    @Test
    void totals_feeDiscountProfit_multiCurrency() {
        SalesOrderItemDO a = item(5, "50.00", "200", true);
        SalesOrderItemDO b = item(10, "50.00", "200", true);
        SalesOrderDO o = order("7.15", "20.00", "100.00", true);
        OrderTotals.applyLine(a, o.getExchangeRate());
        OrderTotals.applyLine(b, o.getExchangeRate());
        OrderTotals.applyTotals(o, List.of(a, b), 1, new BigDecimal("10"));
        eq("750.00", o.getItemAmount());
        eq("75.00", o.getDiscountAmount());
        eq("536.25", o.getDiscountAmountCny());
        eq("695.00", o.getTotalAmount());
        eq("4969.25", o.getTotalAmountCny());
        // 净利润：(750 − 75) × 7.15 − 3000 = 1826.25 CNY
        eq("1826.25", o.getNetProfitCny());
        eq("255.42", o.getNetProfit());
        eq("37.84", o.getMarginRate());

        // EUR 手动订单：没有净利润保持为空，没有费用、折扣
        SalesOrderItemDO m = item(2, "120.00", null, false);
        SalesOrderDO eur = order("7.80", null, null, false);
        OrderTotals.applyLine(m, eur.getExchangeRate());
        OrderTotals.applyTotals(eur, List.of(m), null, null);
        eq("240.00", eur.getTotalAmount());
        eq("1872.00", eur.getTotalAmountCny());
        assertNull(eur.getNetProfit());
    }

    @Test
    void totals_noCostedRevenue_orAllZero() {
        // 有净利润的订单，剩下的行都没有成本：净利润为 0、毛利率为空
        SalesOrderItemDO a = item(2, "50.00", null, false);
        SalesOrderDO o = order("7.15", "0", "0", true);
        OrderTotals.applyLine(a, o.getExchangeRate());
        OrderTotals.applyTotals(o, List.of(a), 0, null);
        eq("100.00", o.getTotalAmount());
        eq("0", o.getNetProfit());
        assertNull(o.getMarginRate());

        // 小计为 0（全部免费行）：毛利率为空
        SalesOrderItemDO free = item(1, "0.00", "10", true);
        SalesOrderDO z = order("7.15", "0", "0", true);
        OrderTotals.applyLine(free, z.getExchangeRate());
        OrderTotals.applyTotals(z, List.of(free), 2, BigDecimal.TEN);
        eq("0.00", z.getDiscountAmount());
        eq("0", z.getNetProfit());
        assertNull(z.getMarginRate());
    }
}
