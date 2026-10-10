package com.zhul.erp.modules.sales.support;

import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.sales.constants.SalesConstants;
import com.zhul.erp.modules.sales.entity.PaymentReceiptDO;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 到账的实收与实收人民币：实收 = 到账金额 − 平台手续费；实收人民币默认 = 实收 × 系统汇率（HALF_UP 2 位，汇率来源「系统汇率」）；
 * 已结汇时填实际入账人民币，汇率 = 实收人民币 ÷ 实收（HALF_UP 6 位，来源「实际入账」）。毛额折算 amount_cny 按同一汇率。
 */
public final class ReceiptMoney {

    private ReceiptMoney() {
    }

    public static void apply(PaymentReceiptDO r, BigDecimal amount, BigDecimal platformFee, BigDecimal systemRate, BigDecimal actualCny) {
        BigDecimal fee = platformFee == null ? BigDecimal.ZERO : platformFee.setScale(2, RoundingMode.HALF_UP);
        if (fee.signum() < 0) {
            throw new BizException("平台手续费不能为负");
        }
        if (fee.compareTo(amount) > 0) {
            throw new BizException("平台手续费不能超过付款金额");
        }
        BigDecimal net = amount.subtract(fee);
        BigDecimal rate;
        BigDecimal netCny;
        if (actualCny != null) {
            if (actualCny.signum() <= 0) {
                throw new BizException("实际入账人民币需要大于 0");
            }
            if (net.signum() <= 0) {
                throw new BizException("实收金额为 0，不能填实际入账人民币");
            }
            netCny = actualCny.setScale(2, RoundingMode.HALF_UP);
            rate = netCny.divide(net, 6, RoundingMode.HALF_UP);
            r.setRateSource(SalesConstants.RATE_ACTUAL);
        } else {
            rate = systemRate;
            netCny = net.multiply(rate).setScale(2, RoundingMode.HALF_UP);
            r.setRateSource(SalesConstants.RATE_SYSTEM);
        }
        r.setAmount(amount);
        r.setPlatformFee(fee);
        r.setNetAmount(net);
        r.setExchangeRate(rate);
        r.setNetAmountCny(netCny);
        r.setAmountCny(amount.multiply(rate).setScale(2, RoundingMode.HALF_UP));
    }
}
