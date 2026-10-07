package com.zhul.erp.modules.sales.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.modules.sales.constants.SalesConstants;
import com.zhul.erp.modules.sales.entity.PaymentReceiptDO;
import com.zhul.erp.modules.sales.entity.PiVersionDO;
import com.zhul.erp.modules.sales.entity.ProformaInvoiceDO;
import com.zhul.erp.modules.sales.repository.PaymentReceiptMapper;
import com.zhul.erp.modules.sales.repository.PiItemMapper;
import com.zhul.erp.modules.sales.entity.PiItemDO;
import com.zhul.erp.modules.sales.repository.ProformaInvoiceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * PI 表头的汇总缓存：型号数与合计取当前有效版本（没有时取编辑中的版本）；
 * 收款状态按有效收款记录重算——已到账 + 手续费差额 ≥ 合计为已到账，有到账为部分到账，只有水单为待到账，否则未付款。
 */
@Component
@RequiredArgsConstructor
public class PiSummary {

    private final ProformaInvoiceMapper piMapper;
    private final PiItemMapper itemMapper;
    private final PaymentReceiptMapper receiptMapper;
    private final PiStore store;

    public void refresh(ProformaInvoiceDO pi) {
        PiVersionDO v = store.version(pi.getId(), pi.getCurrentVersionNo() != null && pi.getCurrentVersionNo() > 0
                ? pi.getCurrentVersionNo() : pi.getEditingVersionNo());
        if (v != null) {
            pi.setItemCount(Math.toIntExact(itemMapper.selectCount(new LambdaQueryWrapper<PiItemDO>()
                    .eq(PiItemDO::getVersionId, v.getId())
                    .isNull(PiItemDO::getDeletedAt))));
            pi.setTotalAmount(v.getTotalAmount());
            pi.setTotalAmountCny(v.getTotalAmountCny());
        }
        List<PaymentReceiptDO> valid = receiptMapper.selectList(new LambdaQueryWrapper<PaymentReceiptDO>()
                .eq(PaymentReceiptDO::getPiId, pi.getId())
                .eq(PaymentReceiptDO::getStatus, SalesConstants.RECORD_VALID)
                .isNull(PaymentReceiptDO::getDeletedAt));
        BigDecimal received = BigDecimal.ZERO;
        BigDecimal feeDiff = BigDecimal.ZERO;
        boolean hasSlip = false;
        for (PaymentReceiptDO r : valid) {
            if (r.getKind() == SalesConstants.KIND_RECEIPT) {
                received = received.add(r.getAmount());
                feeDiff = feeDiff.add(r.getFeeDiff());
            } else {
                hasSlip = true;
            }
        }
        pi.setReceivedAmount(received);
        pi.setFeeDiffAmount(feeDiff);
        BigDecimal total = pi.getTotalAmount() == null ? BigDecimal.ZERO : pi.getTotalAmount();
        if (received.signum() > 0 && received.add(feeDiff).compareTo(total) >= 0) {
            pi.setReceiptStatus(SalesConstants.RECEIPT_PAID);
        } else if (received.signum() > 0) {
            pi.setReceiptStatus(SalesConstants.RECEIPT_PARTIAL);
        } else if (hasSlip) {
            pi.setReceiptStatus(SalesConstants.RECEIPT_SLIP_ONLY);
        } else {
            pi.setReceiptStatus(SalesConstants.RECEIPT_NONE);
        }
        piMapper.updateById(pi);
    }
}
