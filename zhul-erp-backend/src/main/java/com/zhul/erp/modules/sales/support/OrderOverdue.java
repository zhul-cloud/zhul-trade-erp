package com.zhul.erp.modules.sales.support;

import com.zhul.erp.modules.sales.constants.SalesConstants;
import com.zhul.erp.modules.sales.entity.SalesOrderDO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 订单逾期未收款（spec sales/sales-order「订单逾期未收款标记」）：收款记在订单上的有效订单（手动、费用订单），
 * 约定付款日早于今天、收款状态不是「已到账」、剩余未收大于 0。订单详情与老板看板共用这一个判断。
 */
public final class OrderOverdue {

    private OrderOverdue() {
    }

    /** 逾期天数（今天 − 约定付款日）；不逾期时为 null */
    public static Long days(SalesOrderDO o, Integer receiptStatus, BigDecimal remaining, LocalDate today) {
        LocalDate due = o.getPaymentDueDate();
        if (due == null || !due.isBefore(today) || !SalesConstants.ordersOwnReceipts(o.getSource())
                || !Integer.valueOf(SalesConstants.SO_ACTIVE).equals(o.getStatus())
                || Integer.valueOf(SalesConstants.RECEIPT_PAID).equals(receiptStatus)
                || remaining == null || remaining.signum() <= 0) {
            return null;
        }
        return ChronoUnit.DAYS.between(due, today);
    }
}
