package com.zhul.erp.modules.purchase.support;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * 采购价与砍价：
 * 人民币单价 = 单价 × 汇率；砍价金额 = (目标价 − 人民币单价) × 数量；砍价率 = 砍价 ÷ 目标金额；开票成本 = 开票金额 × 税点。
 * 中间过程保持完整精度，金额最后按 HALF_UP 保留 2 位。
 */
public final class PurchaseCalc {

    private static final MathContext MC = MathContext.DECIMAL128;
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private PurchaseCalc() {
    }

    /** 完整精度的人民币单价；单价为空时返回 null */
    public static BigDecimal netPriceExact(BigDecimal unitPrice, BigDecimal rate) {
        return unitPrice == null ? null : unitPrice.multiply(rate, MC);
    }

    public static BigDecimal netPrice(BigDecimal unitPrice, BigDecimal rate) {
        BigDecimal exact = netPriceExact(unitPrice, rate);
        return exact == null ? null : money(exact);
    }

    /** 行砍价金额；没有目标价或单价时为空 */
    public static BigDecimal bargain(BigDecimal targetPrice, BigDecimal unitPrice, int quantity, BigDecimal rate) {
        BigDecimal net = netPriceExact(unitPrice, rate);
        if (targetPrice == null || net == null) {
            return null;
        }
        return money(targetPrice.subtract(net, MC).multiply(BigDecimal.valueOf(quantity), MC));
    }

    /** 开票成本 = 开票金额 × 税点 ÷ 100 */
    public static BigDecimal invoiceCost(BigDecimal invoiceAmount, BigDecimal taxPoint) {
        if (invoiceAmount == null || taxPoint == null) {
            return BigDecimal.ZERO.setScale(2);
        }
        return money(invoiceAmount.multiply(taxPoint, MC).divide(HUNDRED, MC));
    }

    /** 目标金额 = 目标价 × 数量；没有目标价为 0 */
    public static BigDecimal targetAmount(BigDecimal targetPrice, int quantity) {
        return targetPrice == null ? BigDecimal.ZERO : money(targetPrice.multiply(BigDecimal.valueOf(quantity)));
    }

    /** 砍价率（%，两位小数）；目标金额为 0 时为空 */
    public static BigDecimal rate(BigDecimal bargain, BigDecimal targetAmount) {
        if (bargain == null || targetAmount == null || targetAmount.signum() == 0) {
            return null;
        }
        return bargain.multiply(HUNDRED).divide(targetAmount, 2, RoundingMode.HALF_UP);
    }

    public static BigDecimal money(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }
}
