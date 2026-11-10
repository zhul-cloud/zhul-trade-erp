package com.zhul.erp.modules.sales.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.modules.sales.constants.SalesConstants;
import com.zhul.erp.modules.sales.entity.PaymentReceiptDO;
import com.zhul.erp.modules.sales.repository.PaymentReceiptMapper;
import com.zhul.erp.modules.system.notification.constants.NotificationNode;
import com.zhul.erp.modules.system.notification.support.NotificationTodoChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/** 水单待登记：水单已删除，或已有有效的到账记录对应这张水单，就算处理了 */
@Component
@RequiredArgsConstructor
public class ReceiptNotificationChecker implements NotificationTodoChecker {

    private final PaymentReceiptMapper receiptMapper;

    @Override
    public Set<NotificationNode> nodes() {
        return Set.of(NotificationNode.RECEIPT_SLIP);
    }

    @Override
    public Set<Long> open(NotificationNode node, Long recipientId, Collection<Long> bizIds) {
        Set<Long> slips = receiptMapper.selectList(new LambdaQueryWrapper<PaymentReceiptDO>()
                        .select(PaymentReceiptDO::getId)
                        .in(PaymentReceiptDO::getId, bizIds)
                        .eq(PaymentReceiptDO::getKind, SalesConstants.KIND_SLIP)
                        .isNull(PaymentReceiptDO::getDeletedAt))
                .stream().map(PaymentReceiptDO::getId).collect(Collectors.toCollection(HashSet::new));
        if (slips.isEmpty()) {
            return slips;
        }
        receiptMapper.selectList(new LambdaQueryWrapper<PaymentReceiptDO>()
                        .select(PaymentReceiptDO::getSlipId)
                        .in(PaymentReceiptDO::getSlipId, slips)
                        .eq(PaymentReceiptDO::getStatus, SalesConstants.RECORD_VALID)
                        .isNull(PaymentReceiptDO::getDeletedAt))
                .forEach(r -> slips.remove(r.getSlipId()));
        return slips;
    }
}
