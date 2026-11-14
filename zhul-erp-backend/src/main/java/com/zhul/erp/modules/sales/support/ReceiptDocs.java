package com.zhul.erp.modules.sales.support;

import com.zhul.erp.modules.sales.entity.PaymentReceiptDO;
import com.zhul.erp.modules.system.constants.DocumentType;
import com.zhul.erp.modules.system.doclog.DocLogs;
import com.zhul.erp.modules.system.service.DocumentNumberService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 收款单的单号与操作日志（change finance-cash-docs）：所有生成到账的入口取号，写操作记收款单日志 */
@Component
@RequiredArgsConstructor
public class ReceiptDocs {

    private final DocumentNumberService numbers;
    private final DocLogs docLogs;

    /** 插入前调用：给到账取收款单号 */
    public void number(PaymentReceiptDO r) {
        r.setReceiptNo(numbers.next(DocumentType.SK));
    }

    /** 插入或更新后调用 */
    public void log(PaymentReceiptDO r, String action, String content) {
        docLogs.add(DocumentType.SK, r.getId(), action, content);
    }

    public static String money(PaymentReceiptDO r) {
        return r.getCurrencyCode() + " " + r.getAmount().toPlainString();
    }
}
