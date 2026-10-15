package com.zhul.erp.modules.finance;

import com.zhul.erp.modules.finance.support.OrderCostCalc;
import com.zhul.erp.modules.finance.support.OrderCostCalc.Input;
import com.zhul.erp.modules.finance.support.OrderCostCalc.Item;
import com.zhul.erp.modules.finance.support.OrderCostCalc.Po;
import com.zhul.erp.modules.finance.support.OrderCostCalc.PoLine;
import com.zhul.erp.modules.finance.support.OrderCostCalc.Receipt;
import com.zhul.erp.modules.finance.support.OrderCostCalc.Result;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** spec finance/order-cost：实际成本、收入、毛利与已结算判定 */
class OrderCostCalcTest {

    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    private static void eq(String expected, BigDecimal actual) {
        assertEquals(0, d(expected).compareTo(actual), "期望 " + expected + "，实际 " + actual);
    }

    private static Po cnyPo(long id, String items, String fee, String invoice) {
        BigDecimal total = d(items).add(d(fee)).add(d(invoice));
        return new Po(id, "CNY", BigDecimal.ONE, d(items), d(fee), d(invoice), total, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private static Item item(long id, int qty, String amount, String cost, String dom, String intl, String expense) {
        return new Item(id, qty, d(amount), cost == null ? null : d(cost), d(dom), d(intl), d(expense), true, true);
    }

    private static Receipt settled(String gross, String net, String rate) {
        return new Receipt(d(gross), d(net), BigDecimal.ZERO, d(rate), 3);
    }

    @Test
    void invoiceCostIncluded() {
        // 型号 A × 10，单价 100、开票 3%（开票成本 30）、运费 50；国内 20、国际 80、费用单 30
        Input in = new Input(true, d("2000"), List.of(item(1, 10, "300", null, "20", "80", "30")),
                List.of(new PoLine(11, 100, 1, 10, 10, d("100"), null, null)),
                Map.of(100L, cnyPo(100, "1000", "50", "30")), List.of(settled("2000", "2000", "1")));
        Result r = OrderCostCalc.compute(in);
        eq("1210.00", r.lines().get(0).cost());
        eq("30.00", r.lines().get(0).invoice());
        eq("50.00", r.lines().get(0).poFee());
        assertTrue(r.settled(), r.pending().toString());
    }

    @Test
    void foreignPoUsesPaidCny() {
        Po usd = new Po(200, "USD", d("7.10"), d("1000"), BigDecimal.ZERO, BigDecimal.ZERO, d("1000"), d("600"), d("4290"));
        eq("7.130000", OrderCostCalc.actualRate(usd));
        Input in = new Input(true, d("9000"), List.of(item(2, 10, "1300", null, "0", "0", "0")),
                List.of(new PoLine(21, 200, 2, 10, 10, d("100"), null, null)), Map.of(200L, usd),
                List.of(settled("9000", "9000", "6.9")));
        Result r = OrderCostCalc.compute(in);
        eq("7130.00", r.lines().get(0).purchase());
        assertTrue(r.pending().contains("外币货款没付清"));
        assertFalse(r.settled());

        Po paid = new Po(200, "USD", d("7.10"), d("1000"), BigDecimal.ZERO, BigDecimal.ZERO, d("1000"), d("1000"), d("7150"));
        Result r2 = OrderCostCalc.compute(new Input(true, d("9000"), in.items(), in.lines(), Map.of(200L, paid), in.receipts()));
        eq("7150.00", r2.lines().get(0).purchase());
        assertFalse(r2.pending().contains("外币货款没付清"));
    }

    @Test
    void overpaidForeignPoUsesPaidRateOnPayableAmount() {
        // 多付形成欠款：已付 USD 1,200（¥8,520），应付 1,000 → 已付部分按 7.10 折算 1,000
        Po over = new Po(201, "USD", d("7.00"), d("1000"), BigDecimal.ZERO, BigDecimal.ZERO, d("1000"), d("1200"), d("8520"));
        eq("7.100000", OrderCostCalc.actualRate(over));
        assertFalse(OrderCostCalc.foreignUnpaid(over));
    }

    @Test
    void partialReceiptAndUnordered() {
        // C × 10，采购成本价 90；下单 6 个、单价 80，合格入库 4 个
        Input in = new Input(false, d("1500"), List.of(item(3, 10, "200", "90", "0", "0", "0")),
                List.of(new PoLine(31, 300, 3, 6, 4, d("80"), null, null)), Map.of(300L, cnyPo(300, "480", "0", "0")), List.of());
        Result r = OrderCostCalc.compute(in);
        eq("840.00", r.lines().get(0).purchase());
        assertEquals(2, r.lines().get(0).unreceivedQty());
        assertEquals(4, r.lines().get(0).unorderedQty());
        assertEquals(List.of("货款没收齐", "1 个型号没下采购订单", "1 个型号没入库"), r.pending().subList(0, 3));
        eq("1500.00", r.revenue());
    }

    @Test
    void overOrderedCappedAtItemQuantity() {
        // 型号 5 个，采购订单下了 7 个（多发进闲置库存）：只算 5 个
        Input in = new Input(true, d("1000"), List.of(item(4, 5, "100", "50", "0", "0", "0")),
                List.of(new PoLine(41, 400, 4, 7, 7, d("60"), null, null)), Map.of(400L, cnyPo(400, "420", "0", "0")),
                List.of(settled("1000", "1000", "1")));
        Result r = OrderCostCalc.compute(in);
        eq("300.00", r.lines().get(0).purchase());
        assertEquals(0, r.lines().get(0).unorderedQty());
    }

    @Test
    void discountAndReturnFreight() {
        Input in = new Input(true, d("2000"), List.of(item(5, 10, "300", null, "0", "0", "0")),
                List.of(new PoLine(51, 500, 5, 10, 10, d("100"), d("50"), d("18"))), Map.of(500L, cnyPo(500, "1000", "0", "0")),
                List.of(settled("2000", "2000", "1")));
        OrderCostCalc.Line l = OrderCostCalc.compute(in).lines().get(0);
        eq("50.00", l.claim());
        eq("18.00", l.returnFreight());
        eq("968.00", l.cost());
    }

    @Test
    void grossRevenuePlatformFeeAsCost_unsettledFx() {
        // 信保：毛额 USD 306.20 × 6.69 = 2,048.48，手续费 USD 9.19 × 6.69 = 61.48；采购 1,000、国际运费 395
        Input in = new Input(true, d("2066.85"), List.of(item(6, 1, "306.20", null, "0", "395", "0")),
                List.of(new PoLine(61, 600, 6, 1, 1, d("1000"), null, null)), Map.of(600L, cnyPo(600, "1000", "0", "0")),
                List.of(settled("2048.48", "1987.00", "6.69")));
        Result r = OrderCostCalc.compute(in);
        eq("2048.48", r.revenue());
        eq("61.48", r.receiptFee());
        eq("1456.48", r.cost());
        eq("592.00", r.profit());
        eq("28.90", r.profitRate());
        assertTrue(r.settled());

        Result est = OrderCostCalc.compute(new Input(true, in.salesAmountCny(), in.items(), in.lines(), in.pos(),
                List.of(new Receipt(d("2066.85"), d("2004.82"), BigDecimal.ZERO, d("6.75"), 1))));
        eq("2066.85", est.revenue());
        assertEquals(List.of("收款还没结汇"), est.pending());
    }

    @Test
    void bankFeeDiffCountsInRevenueAndCost() {
        // 合计 USD 1,333.65，到账 1,308.65，中转差额 25（汇率 7）
        Input in = new Input(true, d("9335.55"), List.of(item(7, 1, "1333.65", "0", "0", "0", "0")), List.of(), Map.of(),
                List.of(new Receipt(d("9160.55"), d("9160.55"), d("25"), d("7"), 3)));
        Result r = OrderCostCalc.compute(in);
        eq("9335.55", r.revenue());
        eq("175.00", r.receiptFee());
    }

    @Test
    void revenueSplitTailGoesToLargestLine() {
        Map<Long, BigDecimal> w = new LinkedHashMap<>();
        w.put(1L, d("1"));
        w.put(2L, d("1"));
        w.put(3L, d("1"));
        Map<Long, BigDecimal> s = OrderCostCalc.split(d("100"), w);
        eq("100.00", s.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add));
        eq("33.33", s.get(2L));
    }

    @Test
    void zeroRevenueHasNoRate_missingCostPriceFlagged() {
        Input in = new Input(false, BigDecimal.ZERO, List.of(item(8, 2, "0", null, "0", "0", "0")), List.of(), Map.of(), List.of());
        Result r = OrderCostCalc.compute(in);
        assertNull(r.profitRate());
        assertEquals(1, r.missingCostCount());
        eq("0.00", r.cost());
    }

    @Test
    void notShippedMeansFreightPending() {
        Item notShipped = new Item(9, 1, d("100"), d("50"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false, false);
        Result r = OrderCostCalc.compute(new Input(true, d("700"), List.of(notShipped), List.of(), Map.of(),
                List.of(settled("700", "700", "7"))));
        assertTrue(r.pending().contains("国际运费还没对账"));
    }

    @Test
    void refundCutsProfitAndRateBase() {
        // 收入 2,048.48、成本 1,456.48，退款 334.50 → 毛利 257.50，毛利率 257.50 ÷ 1,713.98 = 15.02%
        Input in = new Input(true, d("2066.85"), List.of(item(6, 1, "306.20", null, "0", "395", "0")),
                List.of(new PoLine(61, 600, 6, 1, 1, d("1000"), null, null)), Map.of(600L, cnyPo(600, "1000", "0", "0")),
                List.of(settled("2048.48", "1987.00", "6.69")), d("334.50"));
        Result r = OrderCostCalc.compute(in);
        eq("334.50", r.refund());
        eq("257.50", r.profit());
        eq("15.02", r.profitRate());
        eq("334.50", r.lines().get(0).refund());
        eq("257.50", r.lines().get(0).profit());
    }
}
