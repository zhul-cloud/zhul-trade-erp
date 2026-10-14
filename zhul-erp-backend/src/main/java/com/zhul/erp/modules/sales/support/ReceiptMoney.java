package com.zhul.erp.modules.sales.support;

import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.sales.constants.SalesConstants;
import com.zhul.erp.modules.sales.entity.PaymentReceiptDO;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 到账的实收与人民币：实收 = 到账金额 − 平台手续费。人民币到账汇率为 1；外币到账登记时为「未结汇」，
 * 人民币按估算汇率（最近一次结汇的汇率，没有时为系统汇率）折算，之后由结汇单按先进先出确定（spec finance/fx-settlement）。
 * 毛额折算 amount_cny 按同一汇率，HALF_UP 2 位。
 */
public final class ReceiptMoney {

    private ReceiptMoney() {
    }

    public static void apply(PaymentReceiptDO r, BigDecimal amount, BigDecimal platformFee, BigDecimal estimateRate) {
        BigDecimal fee = platformFee == null ? BigDecimal.ZERO : platformFee.setScale(2, RoundingMode.HALF_UP);
        if (fee.signum() < 0) {
            throw new BizException("平台手续费不能为负");
        }
        if (fee.compareTo(amount) > 0) {
            throw new BizException("平台手续费不能超过付款金额");
        }
        BigDecimal net = amount.subtract(fee);
        boolean cny = "CNY".equals(r.getCurrencyCode());
        BigDecimal rate = cny ? BigDecimal.ONE.setScale(6) : estimateRate;
        r.setRateSource(cny ? SalesConstants.RATE_SYSTEM : SalesConstants.RATE_ESTIMATE);
        r.setSettledAmount(BigDecimal.ZERO);
        r.setSettleStatus(cny ? SalesConstants.SETTLE_NA : SalesConstants.SETTLE_NONE);
        r.setAmount(amount);
        r.setPlatformFee(fee);
        r.setNetAmount(net);
        r.setExchangeRate(rate);
        r.setNetAmountCny(net.multiply(rate).setScale(2, RoundingMode.HALF_UP));
        r.setAmountCny(amount.multiply(rate).setScale(2, RoundingMode.HALF_UP));
    }
}
