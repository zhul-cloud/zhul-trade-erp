package com.zhul.erp.modules.sales.support;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;

/**
 * 议价测算（与前端 bargain.ts 同口径，见 openspec/changes/add-pi-bargain-calculator/design.md）：
 * 有采购成本价的行参与毛利，费用不计入；整单折扣按收入比例分摊到各行；
 * 整单红线 = 有成本行红线按收入加权（没有红线按 0）；
 * 总折扣上限 D_max = 小计 × (1 − 成本 ÷ ((1 − 红线) × 有成本行收入 × 汇率))，「还能再让」= D_max − 当前折扣，
 * 金额向下取到分、点数（占小计的百分比）向下取到 0.1。中间不舍入。
 */
public final class PiBargain {

    private static final MathContext MC = MathContext.DECIMAL128;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private PiBargain() {
    }

    /** 型号行：数量、采购成本单价（CNY，可空）、小计（原币）、红线（%，可空） */
    public record Line(int quantity, BigDecimal costPrice, BigDecimal amount, BigDecimal floorMargin) {
        public BigDecimal cost() {
            return costPrice == null ? null : costPrice.multiply(BigDecimal.valueOf(quantity));
        }
    }

    /** 整单汇总：小计、有成本行收入（原币）、成本合计（CNY）、加权红线（%）；没有有成本行时 costed 为 false */
    public record Summary(BigDecimal itemAmount, BigDecimal costedRevenue, BigDecimal costTotal, BigDecimal floorMargin, boolean costed) {
    }

    public static Summary summarize(List<Line> lines) {
        BigDecimal itemAmount = BigDecimal.ZERO;
        BigDecimal revenue = BigDecimal.ZERO;
        BigDecimal cost = BigDecimal.ZERO;
        BigDecimal floorWeighted = BigDecimal.ZERO;
        boolean costed = false;
        for (Line l : lines) {
            itemAmount = itemAmount.add(l.amount());
            if (l.costPrice() != null) {
                costed = true;
                revenue = revenue.add(l.amount());
                cost = cost.add(l.cost());
                floorWeighted = floorWeighted.add(l.amount().multiply(l.floorMargin() == null ? BigDecimal.ZERO : l.floorMargin()));
            }
        }
        BigDecimal floor = revenue.signum() > 0 ? floorWeighted.divide(revenue, MC) : BigDecimal.ZERO;
        return new Summary(itemAmount, revenue, cost, floor, costed && revenue.signum() > 0);
    }

    /** 折扣 D 后的整单毛利率（%）；不能计算时为空 */
    public static BigDecimal marginAfter(Summary s, BigDecimal discount, BigDecimal rate) {
        BigDecimal revenueCny = revenueCnyAfter(s, discount, rate);
        if (!s.costed() || revenueCny.signum() <= 0) {
            return null;
        }
        return BigDecimal.ONE.subtract(s.costTotal().divide(revenueCny, MC)).multiply(HUNDRED);
    }

    /** 折扣 D 后的毛利（CNY） */
    public static BigDecimal profitAfter(Summary s, BigDecimal discount, BigDecimal rate) {
        return revenueCnyAfter(s, discount, rate).subtract(s.costTotal());
    }

    /** 折扣 D 分摊后，有成本行的收入折合 CNY */
    public static BigDecimal revenueCnyAfter(Summary s, BigDecimal discount, BigDecimal rate) {
        if (s.itemAmount().signum() == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal allocated = discount.multiply(s.costedRevenue(), MC).divide(s.itemAmount(), MC);
        return s.costedRevenue().subtract(allocated).multiply(rate, MC);
    }

    /** 在当前折扣基础上还能再让的金额（原币，向下取到分，不小于 0）；不能计算时为空 */
    public static BigDecimal maxExtraDiscount(Summary s, BigDecimal currentDiscount, BigDecimal rate) {
        if (!s.costed() || s.itemAmount().signum() == 0) {
            return null;
        }
        BigDecimal keep = BigDecimal.ONE.subtract(s.floorMargin().divide(HUNDRED, MC));
        if (keep.signum() <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        BigDecimal ratio = s.costTotal().divide(keep.multiply(s.costedRevenue(), MC).multiply(rate, MC), MC);
        BigDecimal max = s.itemAmount().multiply(BigDecimal.ONE.subtract(ratio), MC).subtract(currentDiscount);
        return max.signum() <= 0 ? BigDecimal.ZERO.setScale(2) : max.setScale(2, RoundingMode.DOWN);
    }

    /** 金额占小计的点数，向下取到 0.1 */
    public static BigDecimal points(BigDecimal amount, BigDecimal itemAmount) {
        if (itemAmount.signum() == 0) {
            return BigDecimal.ZERO.setScale(1);
        }
        return amount.multiply(HUNDRED).divide(itemAmount, MC).setScale(1, RoundingMode.DOWN);
    }
}
