package com.zhul.erp.modules.finance.support;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 订单实际成本与毛利的纯计算（spec finance/order-cost），不读库，便于单元测试。
 * 中间过程保持完整精度，各项按 HALF_UP 保留两位；按比例分到各行时尾差放到金额最大的行。
 * 口径：
 * <ul>
 *   <li>采购货款 = 单价 × 数量 × 实际汇率；数量分三段：合格入库（已确定）、已下单未入库、未下单（按采购成本价）；
 *       采购订单行数量合计超过型号数量时按型号数量封顶；</li>
 *   <li>外币实际汇率 =（已付部分 × 已付汇率 + 未付部分 × 下单汇率）÷ 应付金额，人民币为 1；</li>
 *   <li>开票成本与其他费用按行货款比例分到行；折价（质量索赔）减、退货运费加；</li>
 *   <li>收入：已到账时 = Σ 毛额折人民币 + Σ 中转差额折人民币，否则 = 销售额折人民币；
 *       收款手续费 = Σ 平台手续费折人民币 + Σ 中转差额折人民币；两者按行金额分到行。</li>
 * </ul>
 */
public final class OrderCostCalc {

    private static final MathContext MC = MathContext.DECIMAL128;

    private OrderCostCalc() {
    }

    /** 采购订单（只取已下单的） */
    public record Po(long id, String currency, BigDecimal orderRate, BigDecimal itemAmount, BigDecimal feeAmount,
                     BigDecimal invoiceCost, BigDecimal payableAmount, BigDecimal paidAmount, BigDecimal paidCny) {
    }

    /** 采购订单行：订购数量、合格入库数量、原币单价；折价为原币，退货运费为人民币 */
    public record PoLine(long poItemId, long poId, long soItemId, int quantity, int qualified, BigDecimal unitPrice,
                         BigDecimal discount, BigDecimal returnFreight) {
    }

    /** 订单型号行：数量、行金额（原币，用于分收入）、采购成本价（CNY，可空）；运费与费用单为人民币 */
    public record Item(long id, int quantity, BigDecimal amount, BigDecimal costPrice, BigDecimal domesticFreight,
                       BigDecimal internationalFreight, BigDecimal expense, boolean shipped, boolean freightReconciled) {
    }

    /** 到账：毛额折人民币、实收折人民币、中转差额（原币）、到账汇率、结汇状态（0 不适用、1 未结汇、2 部分、3 已结汇） */
    public record Receipt(BigDecimal amountCny, BigDecimal netAmountCny, BigDecimal feeDiff, BigDecimal rate, int settleStatus) {
    }

    /** refund：有效退款单的人民币合计，从收入中扣减（spec finance/customer-refund） */
    public record Input(boolean paid, BigDecimal salesAmountCny, List<Item> items, List<PoLine> lines, Map<Long, Po> pos,
                        List<Receipt> receipts, BigDecimal refund) {
        public Input(boolean paid, BigDecimal salesAmountCny, List<Item> items, List<PoLine> lines, Map<Long, Po> pos,
                     List<Receipt> receipts) {
            this(paid, salesAmountCny, items, lines, pos, receipts, BigDecimal.ZERO);
        }
    }

    public record Line(long soItemId, BigDecimal purchase, boolean purchaseSettled, BigDecimal invoice, BigDecimal poFee,
                       BigDecimal claim, BigDecimal returnFreight, BigDecimal domestic, BigDecimal international,
                       BigDecimal expense, BigDecimal receiptFee, BigDecimal cost, BigDecimal revenue, BigDecimal refund,
                       BigDecimal profit,
                       BigDecimal profitRate, int unreceivedQty, int unorderedQty, boolean missingCostPrice,
                       Set<Long> unpaidForeignPos) {
    }

    public record Result(BigDecimal revenue, BigDecimal refund, BigDecimal cost, BigDecimal receiptFee, BigDecimal profit,
                         BigDecimal profitRate,
                         boolean settled, List<String> pending, int missingCostCount, List<Line> lines) {
    }

    // ---------------------------------------------------------------- 汇率与金额工具

    /** 采购订单的实际汇率（6 位）：人民币为 1；已付部分按实付汇率，未付部分按下单汇率 */
    public static BigDecimal actualRate(Po po) {
        if ("CNY".equals(po.currency())) {
            return BigDecimal.ONE;
        }
        BigDecimal amount = nz(po.payableAmount());
        BigDecimal paid = nz(po.paidAmount());
        if (amount.signum() <= 0 || paid.signum() <= 0) {
            return po.orderRate();
        }
        BigDecimal paidRate = nz(po.paidCny()).divide(paid, MC);
        BigDecimal used = paid.min(amount);
        BigDecimal cny = used.multiply(paidRate, MC).add(amount.subtract(used).multiply(po.orderRate(), MC));
        return cny.divide(amount, 6, RoundingMode.HALF_UP);
    }

    public static boolean foreignUnpaid(Po po) {
        return !"CNY".equals(po.currency()) && nz(po.paidAmount()).compareTo(nz(po.payableAmount())) < 0;
    }

    /** 按权重把总额分到各键，HALF_UP 两位，尾差放到权重最大的键 */
    public static <K> Map<K, BigDecimal> split(BigDecimal total, Map<K, BigDecimal> weights) {
        Map<K, BigDecimal> out = new HashMap<>();
        BigDecimal sum = weights.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (weights.isEmpty()) {
            return out;
        }
        BigDecimal t = money(total);
        if (sum.signum() == 0) {
            K first = weights.keySet().iterator().next();
            weights.keySet().forEach(k -> out.put(k, BigDecimal.ZERO.setScale(2)));
            out.put(first, t);
            return out;
        }
        K biggest = weights.entrySet().stream().max(Comparator.comparing(Map.Entry::getValue)).orElseThrow().getKey();
        BigDecimal used = BigDecimal.ZERO;
        for (Map.Entry<K, BigDecimal> e : weights.entrySet()) {
            if (e.getKey().equals(biggest)) {
                continue;
            }
            BigDecimal v = money(t.multiply(e.getValue(), MC).divide(sum, MC));
            out.put(e.getKey(), v);
            used = used.add(v);
        }
        out.put(biggest, t.subtract(used));
        return out;
    }

    // ---------------------------------------------------------------- 计算

    public static Result compute(Input in) {
        // 收入与收款手续费（订单级）
        BigDecimal gross = BigDecimal.ZERO;
        BigDecimal fee = BigDecimal.ZERO;
        boolean unsettledFx = false;
        for (Receipt r : in.receipts()) {
            BigDecimal diffCny = nz(r.feeDiff()).multiply(nz(r.rate()), MC);
            gross = gross.add(nz(r.amountCny())).add(diffCny);
            fee = fee.add(nz(r.amountCny()).subtract(nz(r.netAmountCny()))).add(diffCny);
            unsettledFx |= r.settleStatus() == 1 || r.settleStatus() == 2;
        }
        BigDecimal revenue = money(in.paid() ? gross : nz(in.salesAmountCny()));
        BigDecimal receiptFee = money(fee);
        Map<Long, BigDecimal> weights = new java.util.LinkedHashMap<>();
        in.items().forEach(i -> weights.put(i.id(), nz(i.amount())));
        Map<Long, BigDecimal> revenueByItem = split(revenue, weights);
        Map<Long, BigDecimal> feeByItem = split(receiptFee, weights);
        BigDecimal refund = money(nz(in.refund()));
        Map<Long, BigDecimal> refundByItem = split(refund, weights);

        // 采购订单行按型号行分组；采购订单级的开票成本与费用按行货款比例分
        Map<Long, List<PoLine>> linesByItem = new HashMap<>();
        in.lines().forEach(l -> linesByItem.computeIfAbsent(l.soItemId(), k -> new ArrayList<>()).add(l));
        Map<Long, BigDecimal> rates = new HashMap<>();
        in.pos().forEach((id, po) -> rates.put(id, actualRate(po)));

        List<Line> out = new ArrayList<>();
        int missing = 0;
        int unreceivedItems = 0;
        int unorderedItems = 0;
        boolean unpaid = false;
        boolean freightPending = false;
        for (Item item : in.items()) {
            BigDecimal purchase = BigDecimal.ZERO;
            BigDecimal invoice = BigDecimal.ZERO;
            BigDecimal poFee = BigDecimal.ZERO;
            BigDecimal claim = BigDecimal.ZERO;
            BigDecimal returnFreight = BigDecimal.ZERO;
            int cap = item.quantity();
            int ordered = 0;
            int qualified = 0;
            Set<Long> unpaidPos = new LinkedHashSet<>();
            for (PoLine l : linesByItem.getOrDefault(item.id(), List.of())) {
                Po po = in.pos().get(l.poId());
                if (po == null || cap <= 0) {
                    continue;
                }
                int eff = Math.min(l.quantity(), cap);
                cap -= eff;
                ordered += eff;
                qualified += Math.min(l.qualified(), eff);
                BigDecimal rate = rates.get(l.poId());
                BigDecimal goods = nz(l.unitPrice()).multiply(BigDecimal.valueOf(eff), MC);
                purchase = purchase.add(goods.multiply(rate, MC));
                BigDecimal share = nz(po.itemAmount()).signum() == 0 ? BigDecimal.ZERO : goods.divide(po.itemAmount(), MC);
                invoice = invoice.add(nz(po.invoiceCost()).multiply(share, MC).multiply(rate, MC));
                poFee = poFee.add(nz(po.feeAmount()).multiply(share, MC).multiply(rate, MC));
                claim = claim.add(nz(l.discount()).multiply(rate, MC));
                returnFreight = returnFreight.add(nz(l.returnFreight()));
                if (foreignUnpaid(po)) {
                    unpaidPos.add(po.id());
                }
            }
            int unordered = Math.max(0, item.quantity() - ordered);
            boolean missingCost = unordered > 0 && item.costPrice() == null;
            if (unordered > 0 && item.costPrice() != null) {
                purchase = purchase.add(item.costPrice().multiply(BigDecimal.valueOf(unordered), MC));
            }
            if (missingCost) {
                missing++;
            }
            int unreceived = ordered - qualified;
            if (unreceived > 0) {
                unreceivedItems++;
            }
            if (unordered > 0) {
                unorderedItems++;
            }
            unpaid |= !unpaidPos.isEmpty();
            freightPending |= !item.shipped() || !item.freightReconciled();

            BigDecimal rev = revenueByItem.getOrDefault(item.id(), BigDecimal.ZERO.setScale(2));
            BigDecimal rf = feeByItem.getOrDefault(item.id(), BigDecimal.ZERO.setScale(2));
            BigDecimal p = money(purchase);
            BigDecimal inv = money(invoice);
            BigDecimal f = money(poFee);
            BigDecimal c = money(claim);
            BigDecimal rt = money(returnFreight);
            BigDecimal dom = money(nz(item.domesticFreight()));
            BigDecimal intl = money(nz(item.internationalFreight()));
            BigDecimal ex = money(nz(item.expense()));
            BigDecimal cost = p.add(inv).add(f).subtract(c).add(rt).add(dom).add(intl).add(ex).add(rf);
            BigDecimal rf2 = refundByItem.getOrDefault(item.id(), BigDecimal.ZERO.setScale(2));
            BigDecimal profit = rev.subtract(rf2).subtract(cost);
            out.add(new Line(item.id(), p, unordered == 0 && unreceived == 0 && unpaidPos.isEmpty(), inv, f, c, rt, dom, intl, ex, rf,
                    cost, rev, rf2, profit, rate(profit, rev.subtract(rf2)), unreceived, unordered, missingCost, unpaidPos));
        }
        BigDecimal cost = out.stream().map(Line::cost).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal profit = revenue.subtract(refund).subtract(cost);

        List<String> pending = new ArrayList<>();
        if (!in.paid()) {
            pending.add("货款没收齐");
        } else if (unsettledFx) {
            pending.add("收款还没结汇");
        }
        if (unorderedItems > 0) {
            pending.add(unorderedItems + " 个型号没下采购订单");
        }
        if (unreceivedItems > 0) {
            pending.add(unreceivedItems + " 个型号没入库");
        }
        if (unpaid) {
            pending.add("外币货款没付清");
        }
        if (freightPending) {
            pending.add("国际运费还没对账");
        }
        return new Result(revenue, refund, cost, receiptFee, profit, rate(profit, revenue.subtract(refund)), pending.isEmpty(), pending,
                missing, out);
    }

    /** 毛利率（%，两位）；收入为 0 时为空 */
    public static BigDecimal rate(BigDecimal profit, BigDecimal revenue) {
        if (revenue == null || revenue.signum() == 0) {
            return null;
        }
        return profit.multiply(BigDecimal.valueOf(100)).divide(revenue, 2, RoundingMode.HALF_UP);
    }

    public static BigDecimal money(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
