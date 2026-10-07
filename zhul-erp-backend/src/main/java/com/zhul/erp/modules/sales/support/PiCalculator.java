package com.zhul.erp.modules.sales.support;

import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.quotation.support.QuotationPricing;
import com.zhul.erp.modules.sales.constants.SalesConstants;
import com.zhul.erp.modules.sales.entity.PiFeeDO;
import com.zhul.erp.modules.sales.entity.PiItemDO;
import com.zhul.erp.modules.sales.entity.PiVersionDO;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;

/**
 * PI 计算：型号行一律按「直接填外币售价」口径（单价来自报价单，可改），成本取快照；
 * 整单折扣按百分比时 = 型号小计 × 百分比（2 位 HALF_UP），不能超过型号小计；
 * 合计 = 型号小计 + 费用 − 折扣；合计净利润 = Σ有成本行净利润 − 折扣分摊到有成本行的部分；
 * 合计毛利率 = 1 − Σ成本 ÷ ((有成本行收入 − 分摊折扣) × 汇率)。中间不舍入，输出 2 位 HALF_UP。
 */
public final class PiCalculator {

    private static final MathContext MC = MathContext.DECIMAL128;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private PiCalculator() {
    }

    public static void applyLine(PiItemDO i, BigDecimal rate) {
        if (i.getUnitPrice() == null || i.getUnitPrice().signum() < 0) {
            throw new BizException("单价不能为负");
        }
        QuotationPricing.Result r = QuotationPricing.calculate(new QuotationPricing.Input(i.getCostPrice(), i.getQuantity(), rate,
                QuotationPricing.MODE_PRICE, null, null, i.getUnitPrice()));
        i.setUnitPrice(r.unitPrice());
        i.setUnitPriceCny(r.unitPriceCny());
        i.setAmount(r.amount());
        i.setAmountCny(r.amountCny());
        i.setMarginRate(r.marginRate());
        i.setNetProfit(r.netProfit());
        i.setNetProfitCny(r.netProfitCny());
    }

    public static void applyTotals(PiVersionDO v, List<PiItemDO> items, List<PiFeeDO> fees, BigDecimal rate) {
        BigDecimal itemAmount = BigDecimal.ZERO;
        BigDecimal costedRevenue = BigDecimal.ZERO;
        BigDecimal costTotal = BigDecimal.ZERO;
        BigDecimal profit = null;
        BigDecimal profitCny = null;
        for (PiItemDO i : items) {
            itemAmount = itemAmount.add(i.getAmount());
            if (i.getCostPrice() != null) {
                costedRevenue = costedRevenue.add(i.getAmount());
                costTotal = costTotal.add(i.getCostPrice().multiply(BigDecimal.valueOf(i.getQuantity())));
                profit = (profit == null ? BigDecimal.ZERO : profit).add(i.getNetProfit());
                profitCny = (profitCny == null ? BigDecimal.ZERO : profitCny).add(i.getNetProfitCny());
            }
        }
        BigDecimal feeAmount = BigDecimal.ZERO;
        for (PiFeeDO f : fees) {
            f.setAmount(f.getAmount().setScale(2, RoundingMode.HALF_UP));
            f.setAmountCny(f.getAmount().multiply(rate, MC).setScale(2, RoundingMode.HALF_UP));
            feeAmount = feeAmount.add(f.getAmount());
        }
        BigDecimal discount = discount(v.getDiscountType(), v.getDiscountValue(), itemAmount);
        BigDecimal total = itemAmount.add(feeAmount).subtract(discount);
        v.setItemAmount(itemAmount.setScale(2, RoundingMode.HALF_UP));
        v.setFeeAmount(feeAmount.setScale(2, RoundingMode.HALF_UP));
        v.setDiscountAmount(discount);
        v.setDiscountAmountCny(discount.multiply(rate, MC).setScale(2, RoundingMode.HALF_UP));
        v.setTotalAmount(total.setScale(2, RoundingMode.HALF_UP));
        v.setTotalAmountCny(total.multiply(rate, MC).setScale(2, RoundingMode.HALF_UP));
        if (profit == null || itemAmount.signum() == 0) {
            v.setNetProfit(null);
            v.setNetProfitCny(null);
            v.setMarginRate(null);
            return;
        }
        // 折扣按收入比例分摊到有采购成本的行
        BigDecimal allocated = discount.multiply(costedRevenue, MC).divide(itemAmount, MC);
        BigDecimal revenueCny = costedRevenue.subtract(allocated).multiply(rate, MC);
        v.setNetProfit(costedRevenue.subtract(allocated).subtract(costTotal.divide(rate, MC)).setScale(2, RoundingMode.HALF_UP));
        v.setNetProfitCny(revenueCny.subtract(costTotal).setScale(2, RoundingMode.HALF_UP));
        v.setMarginRate(revenueCny.signum() > 0
                ? BigDecimal.ONE.subtract(costTotal.divide(revenueCny, MC)).multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP) : null);
    }

    /** 折扣金额（正数）；按百分比时 0–100，按金额时不能为负；都不能超过型号小计 */
    public static BigDecimal discount(Integer type, BigDecimal value, BigDecimal itemAmount) {
        int t = type == null ? SalesConstants.DISCOUNT_NONE : type;
        if (t == SalesConstants.DISCOUNT_NONE || value == null || value.signum() == 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        if (value.signum() < 0) {
            throw new BizException("折扣不能为负");
        }
        BigDecimal d;
        if (t == SalesConstants.DISCOUNT_PERCENT) {
            if (value.compareTo(HUNDRED) > 0) {
                throw new BizException("折扣百分比需要在 0–100% 之间");
            }
            d = itemAmount.multiply(value, MC).divide(HUNDRED, MC).setScale(2, RoundingMode.HALF_UP);
        } else if (t == SalesConstants.DISCOUNT_AMOUNT) {
            d = value.setScale(2, RoundingMode.HALF_UP);
        } else {
            throw new BizException("折扣方式不正确");
        }
        if (d.compareTo(itemAmount) > 0) {
            throw new BizException("折扣不能超过型号小计");
        }
        return d;
    }
}
