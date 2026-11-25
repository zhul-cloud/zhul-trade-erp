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
 *   <li>收入：已到账时 = Σ 毛额折人民币 + Σ 中转差额折人民币 − 代收与多收（deduction，spec finance/customer-credit），
 *       否则 = 销售额折人民币（不含代收款）；余额抵扣作为一笔到账按锁定价值计入；
 *       收款手续费 = Σ 平台手续费折人民币 + Σ 中转差额折人民币 − 代收承担的手续费（feeDeduction）；两者按行金额分到行。</li>
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
    /**
     * 订单型号行；salesReturnCredit 为售后退货去向冲减的成本（退回供应商的供应商退款 + 转闲置按原采购成本，正数为冲减），
     * salesReturnFreight 为售后退货退回供应商的运费（计入「退货运费」）；estimatedFreight 为还没出运的数量按订单运费估算的
     * 国际运费（人民币两位，见 {@link #estimatedFreight}）。
     */
    public record Item(long id, int quantity, BigDecimal amount, BigDecimal costPrice, BigDecimal domesticFreight,
                       BigDecimal internationalFreight, BigDecimal expense, boolean shipped, boolean freightReconciled,
                       BigDecimal salesReturnCredit, BigDecimal salesReturnFreight, BigDecimal estimatedFreight) {
        public Item(long id, int quantity, BigDecimal amount, BigDecimal costPrice, BigDecimal domesticFreight,
                    BigDecimal internationalFreight, BigDecimal expense, boolean shipped, boolean freightReconciled,
                    BigDecimal salesReturnCredit, BigDecimal salesReturnFreight) {
            this(id, quantity, amount, costPrice, domesticFreight, internationalFreight, expense, shipped, freightReconciled,
                    salesReturnCredit, salesReturnFreight, BigDecimal.ZERO);
        }

        public Item(long id, int quantity, BigDecimal amount, BigDecimal costPrice, BigDecimal domesticFreight,
                    BigDecimal internationalFreight, BigDecimal expense, boolean shipped, boolean freightReconciled) {
            this(id, quantity, amount, costPrice, domesticFreight, internationalFreight, expense, shipped, freightReconciled,
                    BigDecimal.ZERO, BigDecimal.ZERO);
        }
    }

    /** 到账：毛额折人民币、实收折人民币、中转差额（原币）、到账汇率、结汇状态（0 不适用、1 未结汇、2 部分、3 已结汇） */
    /** 收款：毛额与实收的人民币、手续费差额、到账汇率、结汇状态、结汇手续费（人民币，结汇时银行扣的） */
    public record Receipt(BigDecimal amountCny, BigDecimal netAmountCny, BigDecimal feeDiff, BigDecimal rate, int settleStatus,
                          BigDecimal fxFeeCny) {
    }

    /**
     * 型号行已出运的结转成本（spec finance/order-cost「已出运的数量读结转成本」）：出运数量、其中来自系统外入库（成本 0）的数量，
     * 四项为结转金额加之后针对这些出运的成本调整（人民币，两位）；claim 为质量索赔冲减额（正数）。
     */
    public record Shipped(int qty, int systemQty, BigDecimal goods, BigDecimal invoice, BigDecimal fee, BigDecimal claim) {
    }

    /** refund：有效退款单的人民币合计，从收入中扣减（spec finance/customer-refund） */
    /** deduction：已到账时从收入扣除的代收与多收（人民币）；feeDeduction：代收承担的平台手续费（人民币，由客户承担） */
    /** shipped：型号行 ID → 已出运的结转成本（没有出运的不在里面） */
    public record Input(boolean paid, BigDecimal salesAmountCny, List<Item> items, List<PoLine> lines, Map<Long, Po> pos,
                        List<Receipt> receipts, BigDecimal refund, BigDecimal deduction, BigDecimal feeDeduction,
                        Map<Long, Shipped> shipped) {
        public Input(boolean paid, BigDecimal salesAmountCny, List<Item> items, List<PoLine> lines, Map<Long, Po> pos,
                     List<Receipt> receipts, BigDecimal refund, BigDecimal deduction, BigDecimal feeDeduction) {
            this(paid, salesAmountCny, items, lines, pos, receipts, refund, deduction, feeDeduction, Map.of());
        }

        public Input(boolean paid, BigDecimal salesAmountCny, List<Item> items, List<PoLine> lines, Map<Long, Po> pos,
                     List<Receipt> receipts) {
            this(paid, salesAmountCny, items, lines, pos, receipts, BigDecimal.ZERO);
        }

        public Input(boolean paid, BigDecimal salesAmountCny, List<Item> items, List<PoLine> lines, Map<Long, Po> pos,
                     List<Receipt> receipts, BigDecimal refund) {
            this(paid, salesAmountCny, items, lines, pos, receipts, refund, BigDecimal.ZERO, BigDecimal.ZERO);
        }
    }

    public record Line(long soItemId, BigDecimal purchase, boolean purchaseSettled, BigDecimal invoice, BigDecimal poFee,
                       BigDecimal claim, BigDecimal returnFreight, BigDecimal domestic, BigDecimal international,
                       BigDecimal expense, BigDecimal receiptFee, BigDecimal cost, BigDecimal revenue, BigDecimal refund,
                       BigDecimal profit,
                       BigDecimal profitRate, int unreceivedQty, int unorderedQty, boolean missingCostPrice,
                       Set<Long> unpaidForeignPos, BigDecimal salesReturnCredit, int shippedQty, int systemQty,
                       BigDecimal estimatedFreight) {
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

    /** 采购订单行一个单位的人民币成本（完整精度，不舍入）：货款、开票成本、采购费用；订单成本与库存批次成本共用 */
    public record UnitCost(BigDecimal goods, BigDecimal invoice, BigDecimal fee) {
        public BigDecimal total() {
            return goods.add(invoice).add(fee);
        }
    }

    /**
     * 单位成本：货款 = 原币单价 × 实际汇率；开票成本、采购费用 = 采购订单的开票成本、费用 × 单价占采购订单货款合计的比例 × 实际汇率
     * （与按行货款比例分摊再除以数量相同）。
     */
    public static UnitCost unitCost(Po po, BigDecimal unitPrice, BigDecimal rate) {
        BigDecimal price = nz(unitPrice);
        BigDecimal share = nz(po.itemAmount()).signum() == 0 ? BigDecimal.ZERO : price.divide(po.itemAmount(), MC);
        return new UnitCost(price.multiply(rate, MC), nz(po.invoiceCost()).multiply(share, MC).multiply(rate, MC),
                nz(po.feeAmount()).multiply(share, MC).multiply(rate, MC));
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
            fee = fee.add(nz(r.amountCny()).subtract(nz(r.netAmountCny()))).add(diffCny).add(nz(r.fxFeeCny()));
            unsettledFx |= r.settleStatus() == 1 || r.settleStatus() == 2;
        }
        BigDecimal revenue = money(in.paid() ? gross.subtract(nz(in.deduction())) : nz(in.salesAmountCny()));
        BigDecimal receiptFee = money(fee.subtract(in.paid() ? nz(in.feeDeduction()) : BigDecimal.ZERO));
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
        boolean shippedUnreconciled = false;
        boolean estimated = false;
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
            // 已出运的数量读结转成本，先占采购订单行的数量；其中系统外入库的按该采购订单行推算，其余只按剩下的数量推算
            Shipped sh = in.shipped() == null ? null : in.shipped().get(item.id());
            int shippedQty = sh == null ? 0 : Math.max(0, Math.min(sh.qty(), item.quantity()));
            int systemQty = sh == null ? 0 : Math.max(0, Math.min(sh.systemQty(), shippedQty));
            int shippedLeft = shippedQty;
            int systemLeft = systemQty;
            if (sh != null) {
                purchase = purchase.add(nz(sh.goods()));
                invoice = invoice.add(nz(sh.invoice()));
                poFee = poFee.add(nz(sh.fee()));
                claim = claim.add(nz(sh.claim()));
            }
            for (PoLine l : linesByItem.getOrDefault(item.id(), List.of())) {
                Po po = in.pos().get(l.poId());
                if (po == null || cap <= 0) {
                    continue;
                }
                int eff = Math.min(l.quantity(), cap);
                cap -= eff;
                int out1 = Math.min(eff, shippedLeft);
                shippedLeft -= out1;
                int sys = Math.min(out1, systemLeft);
                systemLeft -= sys;
                int rest = eff - out1;
                int q = Math.min(l.qualified(), eff);
                ordered += rest;
                qualified += Math.max(0, q - out1);
                BigDecimal rate = rates.get(l.poId());
                UnitCost u = unitCost(po, l.unitPrice(), rate);
                BigDecimal n = BigDecimal.valueOf(rest + sys);
                purchase = purchase.add(u.goods().multiply(n, MC));
                invoice = invoice.add(u.invoice().multiply(n, MC));
                poFee = poFee.add(u.fee().multiply(n, MC));
                // 折价只摊到没出运的合格数量（已出运的已在结转成本里）
                BigDecimal discount = nz(l.discount()).multiply(rate, MC);
                claim = claim.add(q > 0 ? discount.multiply(BigDecimal.valueOf(Math.max(0, q - out1)), MC)
                        .divide(BigDecimal.valueOf(q), MC) : discount);
                returnFreight = returnFreight.add(nz(l.returnFreight()));
                if (foreignUnpaid(po)) {
                    unpaidPos.add(po.id());
                }
            }
            int unordered = Math.max(0, item.quantity() - ordered - shippedQty);
            // 没有采购订单行的系统外入库数量按采购成本价
            int fallback = unordered + systemLeft;
            boolean missingCost = fallback > 0 && item.costPrice() == null;
            if (fallback > 0 && item.costPrice() != null) {
                purchase = purchase.add(item.costPrice().multiply(BigDecimal.valueOf(fallback), MC));
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
            shippedUnreconciled |= item.shipped() && !item.freightReconciled();

            BigDecimal rev = revenueByItem.getOrDefault(item.id(), BigDecimal.ZERO.setScale(2));
            BigDecimal rf = feeByItem.getOrDefault(item.id(), BigDecimal.ZERO.setScale(2));
            BigDecimal p = money(purchase);
            BigDecimal inv = money(invoice);
            BigDecimal f = money(poFee);
            BigDecimal c = money(claim);
            BigDecimal rt = money(returnFreight.add(nz(item.salesReturnFreight())));
            BigDecimal sr = money(nz(item.salesReturnCredit()));
            BigDecimal dom = money(nz(item.domesticFreight()));
            BigDecimal intl = money(nz(item.internationalFreight()));
            BigDecimal est = money(nz(item.estimatedFreight()));
            estimated |= est.signum() > 0;
            BigDecimal ex = money(nz(item.expense()));
            BigDecimal cost = p.add(inv).add(f).subtract(c).add(rt).add(dom).add(intl).add(est).add(ex).add(rf).subtract(sr);
            BigDecimal rf2 = refundByItem.getOrDefault(item.id(), BigDecimal.ZERO.setScale(2));
            BigDecimal profit = rev.subtract(rf2).subtract(cost);
            out.add(new Line(item.id(), p, unordered == 0 && unreceived == 0 && unpaidPos.isEmpty(), inv, f, c, rt, dom, intl, ex, rf,
                    cost, rev, rf2, profit, rate(profit, rev.subtract(rf2)), unreceived, unordered, missingCost, unpaidPos, sr,
                    shippedQty, systemQty, est));
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
            pending.add(estimated && !shippedUnreconciled ? "国际运费还没出运，按订单运费估算" : "国际运费还没对账");
        }
        return new Result(revenue, refund, cost, receiptFee, profit, rate(profit, revenue.subtract(refund)), pending.isEmpty(), pending,
                missing, out);
    }

    /** 估算运费用的型号行：行金额（原币，作分摊权重）、行数量、已出运数量 */
    public record FreightLine(BigDecimal amount, int quantity, int shippedQty) {
    }

    /**
     * 预计国际运费（spec finance/order-cost「订单型号行实际成本」）：订单运费（人民币）按行金额分到各行（尾差放到金额最大的行），
     * 再乘这一行还没出运的数量占比（未出运 ÷ 行数量），HALF_UP 保留两位。运费为 0 或没有时返回空。
     *
     * @param lines 型号行 ID → 行金额、行数量与已出运数量
     */
    public static Map<Long, BigDecimal> estimatedFreight(BigDecimal freightCny, Map<Long, FreightLine> lines) {
        if (freightCny == null || freightCny.signum() <= 0 || lines.isEmpty()) {
            return Map.of();
        }
        Map<Long, BigDecimal> weights = new java.util.LinkedHashMap<>();
        lines.forEach((id, l) -> weights.put(id, nz(l.amount())));
        Map<Long, BigDecimal> share = split(freightCny, weights);
        Map<Long, BigDecimal> out = new HashMap<>();
        lines.forEach((id, l) -> {
            int qty = l.quantity();
            int unshipped = Math.max(0, qty - l.shippedQty());
            if (qty > 0 && unshipped > 0) {
                out.put(id, money(share.get(id).multiply(BigDecimal.valueOf(unshipped), MC)
                        .divide(BigDecimal.valueOf(qty), MC)));
            }
        });
        return out;
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
