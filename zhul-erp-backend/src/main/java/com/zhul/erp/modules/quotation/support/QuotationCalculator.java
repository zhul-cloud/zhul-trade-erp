package com.zhul.erp.modules.quotation.support;

import com.zhul.erp.modules.quotation.entity.QuotationDO;
import com.zhul.erp.modules.quotation.entity.QuotationFeeDO;
import com.zhul.erp.modules.quotation.entity.QuotationItemDO;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;

/**
 * 把定价结果写回型号行并汇总整单：合计 = 型号小计 + 费用；本位币 = 原币 × 汇率（2 位 HALF_UP）；
 * 合计净利润、合计毛利率只按有采购成本价的型号行计算，费用行不参与。
 */
public final class QuotationCalculator {

    private static final MathContext MC = MathContext.DECIMAL128;

    private QuotationCalculator() {
    }

    public static void applyLine(QuotationItemDO item, BigDecimal rate) {
        QuotationPricing.Result r = QuotationPricing.calculate(new QuotationPricing.Input(item.getCostPrice(),
                item.getQuantity(), rate, item.getPricingMode(), item.getMarginRate(), item.getMarkupAmount(), item.getUnitPrice()));
        if (item.getCostPrice() == null) {
            item.setPricingMode(QuotationPricing.MODE_PRICE);
            item.setMarkupAmount(null);
        }
        if (item.getPricingMode() != QuotationPricing.MODE_MARKUP) {
            item.setMarkupAmount(null);
        }
        item.setUnitPrice(r.unitPrice());
        item.setUnitPriceCny(r.unitPriceCny());
        item.setMarginRate(r.marginRate());
        item.setAmount(r.amount());
        item.setAmountCny(r.amountCny());
        item.setNetProfit(r.netProfit());
        item.setNetProfitCny(r.netProfitCny());
    }

    public static void applyTotals(QuotationDO q, List<QuotationItemDO> items, List<QuotationFeeDO> fees) {
        BigDecimal rate = q.getExchangeRate();
        BigDecimal itemAmount = BigDecimal.ZERO;
        BigDecimal netProfit = null;
        BigDecimal netProfitCny = null;
        BigDecimal costTotal = BigDecimal.ZERO;
        BigDecimal revenueCny = BigDecimal.ZERO;
        for (QuotationItemDO i : items) {
            itemAmount = itemAmount.add(i.getAmount());
            if (i.getCostPrice() != null) {
                netProfit = (netProfit == null ? BigDecimal.ZERO : netProfit).add(i.getNetProfit());
                netProfitCny = (netProfitCny == null ? BigDecimal.ZERO : netProfitCny).add(i.getNetProfitCny());
                costTotal = costTotal.add(i.getCostPrice().multiply(BigDecimal.valueOf(i.getQuantity())));
                revenueCny = revenueCny.add(i.getAmount().multiply(rate, MC));
            }
        }
        BigDecimal feeAmount = BigDecimal.ZERO;
        for (QuotationFeeDO f : fees) {
            f.setAmount(f.getAmount().setScale(2, RoundingMode.HALF_UP));
            f.setAmountCny(f.getAmount().multiply(rate, MC).setScale(2, RoundingMode.HALF_UP));
            feeAmount = feeAmount.add(f.getAmount());
        }
        BigDecimal total = itemAmount.add(feeAmount);
        q.setItemAmount(itemAmount.setScale(2, RoundingMode.HALF_UP));
        q.setFeeAmount(feeAmount.setScale(2, RoundingMode.HALF_UP));
        q.setTotalAmount(total.setScale(2, RoundingMode.HALF_UP));
        q.setTotalAmountCny(total.multiply(rate, MC).setScale(2, RoundingMode.HALF_UP));
        q.setNetProfit(netProfit);
        q.setNetProfitCny(netProfitCny);
        q.setMarginRate(revenueCny.signum() > 0
                ? BigDecimal.ONE.subtract(costTotal.divide(revenueCny, MC)).multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP)
                : null);
    }
}
