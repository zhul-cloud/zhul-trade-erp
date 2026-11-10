package com.zhul.erp.modules.purchase.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.modules.purchase.constants.PurchaseConstants;
import com.zhul.erp.modules.purchase.entity.PurchaseRequirementDO;
import com.zhul.erp.modules.purchase.repository.PurchaseRequirementMapper;
import com.zhul.erp.modules.system.notification.constants.NotificationNode;
import com.zhul.erp.modules.system.notification.support.NotificationTodoChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 要采购的型号是否已处理：需求已全部确认下单、关闭或订单取消，或已转给别人（没指定采购员的：已指定）。
 * 只排进草稿、还没确认下单的仍算没处理。
 */
@Component
@RequiredArgsConstructor
public class RequirementNotificationChecker implements NotificationTodoChecker {

    private final PurchaseRequirementMapper requirementMapper;
    private final RequirementQty qty;

    @Override
    public Set<NotificationNode> nodes() {
        return Set.of(NotificationNode.SO_PURCHASE, NotificationNode.SO_NO_PURCHASER);
    }

    @Override
    public Set<Long> open(NotificationNode node, Long recipientId, Collection<Long> bizIds) {
        List<PurchaseRequirementDO> reqs = requirementMapper.selectList(new LambdaQueryWrapper<PurchaseRequirementDO>()
                .in(PurchaseRequirementDO::getId, bizIds)
                .eq(PurchaseRequirementDO::getStatus, PurchaseConstants.REQ_ACTIVE)
                .isNull(PurchaseRequirementDO::getDeletedAt));
        reqs = reqs.stream().filter(r -> node == NotificationNode.SO_PURCHASE
                ? Objects.equals(r.getPurchaserId(), recipientId)
                : r.getPurchaserId() == null || r.getPurchaserId() <= 0).toList();
        if (reqs.isEmpty()) {
            return Set.of();
        }
        Map<Long, RequirementQty.Qty> q = qty.byRequirement(reqs.stream().map(PurchaseRequirementDO::getId).toList());
        return reqs.stream()
                .filter(r -> q.getOrDefault(r.getId(), RequirementQty.Qty.ZERO).ordered() < r.getQuantity())
                .map(PurchaseRequirementDO::getId).collect(Collectors.toSet());
    }
}
