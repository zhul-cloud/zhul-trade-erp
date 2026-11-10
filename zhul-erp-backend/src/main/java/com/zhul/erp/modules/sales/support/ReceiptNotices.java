package com.zhul.erp.modules.sales.support;

import com.zhul.erp.modules.sales.entity.PaymentReceiptDO;
import com.zhul.erp.modules.system.notification.constants.NotificationNode;
import com.zhul.erp.modules.system.notification.support.Notice;
import com.zhul.erp.modules.system.notification.support.Notifier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.List;

/** 收款相关的通知（spec system/notification「收款通知」） */
@Component
@RequiredArgsConstructor
public class ReceiptNotices {

    private final Notifier notifier;

    /** 业务员上传客户付款水单：通知财务去登记到账 */
    public void slipUploaded(PaymentReceiptDO slip, String ownerNo) {
        notifier.send(Notice.of(NotificationNode.RECEIPT_SLIP, ownerNo, "/finance/receipts")
                .merge("slip-" + slip.getId())
                .ids(List.of(slip.getId()))
                .content("客户付款 " + money(slip.getCurrencyCode(), slip.getAmount()) + " · 付款日期 " + slip.getReceiptDate()));
    }

    /** 到账登记到 PI 或订单：通知负责业务员 */
    public void received(String ownerNo, boolean pi, Long ownerDocId, Long salesId, String currency, BigDecimal amount) {
        notifier.send(Notice.of(NotificationNode.RECEIPT_CONFIRMED, ownerNo, (pi ? "/sales/pi/" : "/sales/orders/") + ownerDocId)
                .content("到账 " + money(currency, amount))
                .to(salesId));
    }

    static String money(String currency, BigDecimal amount) {
        return currency + " " + (amount == null ? "0.00" : new DecimalFormat("#,##0.00").format(amount));
    }
}
