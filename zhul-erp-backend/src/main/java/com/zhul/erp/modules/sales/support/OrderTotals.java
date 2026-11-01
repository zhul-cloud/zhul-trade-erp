package com.zhul.erp.modules.sales.support;

import com.zhul.erp.modules.quotation.support.QuotationPricing;
import com.zhul.erp.modules.sales.constants.SalesConstants;
import com.zhul.erp.modules.sales.entity.SalesOrderDO;
import com.zhul.erp.modules.sales.entity.SalesOrderItemDO;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;

/**
 * 部分取消后重算订单金额（spec sales/sales-order「按型号部分取消」）：
 * <ul>
 *   <li>型号行金额 = 剩余数量 × 单价，本位币 = 金额 × 订单汇率，HALF_UP 两位；有净利润的行按剩余数量重算净利润；数量为 0 的行金额、净利润为 0；</li>
 *   <li>整单折扣按百分比时 = 新小计 × 百分比（HALF_UP 两位），按固定金额时不变，超过新小计时按小计封顶；</li>
 *   <li>合计 = 型号小计 + 费用 − 折扣；合计净利润与毛利率口径同 PiCalculator（折扣按收入比例分摊到有成本的行），
 *       原来没有净利润的订单（手动创建）保持为空。中间不舍入。</li>
 * </ul>
 */
public final class OrderTotals {

    private static final MathContext MC = MathContext.DECIMAL128;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private OrderTotals() {
    }

    /** 按剩余数量重算型号行金额与净利润 */
    public static void applyLine(SalesOrderItemDO i, BigDecimal rate) {
        boolean hasProfit = i.getNetProfit() != null;
        if (i.getQuantity() <= 0) {
            i.setAmount(BigDecimal.ZERO.setScale(2));
            i.setAmountCny(BigDecimal.ZERO.setScale(2));
            if (hasProfit) {
                i.setNetProfit(BigDecimal.ZERO.setScale(2));
                i.setNetProfitCny(BigDecimal.ZERO.setScale(2));
            }
            return;
        }
        QuotationPricing.Result r = QuotationPricing.calculate(new QuotationPricing.Input(i.getCostPrice(), i.getQuantity(), rate,
                QuotationPricing.MODE_PRICE, null, null, i.getUnitPrice()));
        i.setAmount(r.amount());
        i.setAmountCny(r.amountCny());
        if (hasProfit) {
            i.setNetProfit(r.netProfit());
            i.setNetProfitCny(r.netProfitCny());
        }
    }

    /** 折扣：按百分比时按新小计重算，按金额时不变但不超过小计 */
    public static BigDecimal discount(Integer type, BigDecimal value, BigDecimal current, BigDecimal itemAmount) {
        int t = type == null ? SalesConstants.DISCOUNT_NONE : type;
        BigDecimal d;
        if (t == SalesConstants.DISCOUNT_PERCENT && value != null) {
            d = itemAmount.multiply(value, MC).divide(HUNDRED, MC).setScale(2, RoundingMode.HALF_UP);
        } else {
            d = current == null ? BigDecimal.ZERO.setScale(2) : current;
        }
        return d.min(itemAmount).setScale(2, RoundingMode.HALF_UP);
    }

    /** 重算订单合计；discountType / discountValue 取 PI 版本（手动订单为无折扣） */
    public static void applyTotals(SalesOrderDO o, List<SalesOrderItemDO> items, Integer discountType, BigDecimal discountValue) {
        BigDecimal rate = o.getExchangeRate();
        BigDecimal itemAmount = BigDecimal.ZERO;
        BigDecimal costedRevenue = BigDecimal.ZERO;
        BigDecimal costTotal = BigDecimal.ZERO;
        BigDecimal profit = null;
        for (SalesOrderItemDO i : items) {
            itemAmount = itemAmount.add(i.getAmount());
            if (i.getCostPrice() != null && i.getNetProfit() != null) {
                costedRevenue = costedRevenue.add(i.getAmount());
                costTotal = costTotal.add(i.getCostPrice().multiply(BigDecimal.valueOf(i.getQuantity())));
                profit = (profit == null ? BigDecimal.ZERO : profit).add(i.getNetProfit());
            }
        }
        BigDecimal fee = o.getFeeAmount() == null ? BigDecimal.ZERO : o.getFeeAmount();
        BigDecimal discount = discount(discountType, discountValue, o.getDiscountAmount(), itemAmount);
        BigDecimal total = itemAmount.add(fee).subtract(discount);
        o.setItemAmount(itemAmount.setScale(2, RoundingMode.HALF_UP));
        o.setDiscountAmount(discount);
        o.setDiscountAmountCny(discount.multiply(rate, MC).setScale(2, RoundingMode.HALF_UP));
        o.setTotalAmount(total.setScale(2, RoundingMode.HALF_UP));
        o.setTotalAmountCny(total.multiply(rate, MC).setScale(2, RoundingMode.HALF_UP));
        if (o.getNetProfit() == null) {
            return;
        }
        if (profit == null || itemAmount.signum() == 0) {
            o.setNetProfit(BigDecimal.ZERO.setScale(2));
            o.setNetProfitCny(BigDecimal.ZERO.setScale(2));
            o.setMarginRate(null);
            return;
        }
        BigDecimal allocated = discount.multiply(costedRevenue, MC).divide(itemAmount, MC);
        BigDecimal revenueCny = costedRevenue.subtract(allocated).multiply(rate, MC);
        o.setNetProfit(costedRevenue.subtract(allocated).subtract(costTotal.divide(rate, MC)).setScale(2, RoundingMode.HALF_UP));
        o.setNetProfitCny(revenueCny.subtract(costTotal).setScale(2, RoundingMode.HALF_UP));
        o.setMarginRate(revenueCny.signum() > 0
                ? BigDecimal.ONE.subtract(costTotal.divide(revenueCny, MC)).multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP) : null);
    }
}
