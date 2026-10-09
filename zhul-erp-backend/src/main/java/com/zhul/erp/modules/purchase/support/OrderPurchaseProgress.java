package com.zhul.erp.modules.purchase.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.modules.purchase.entity.PurchaseRequirementDO;
import com.zhul.erp.modules.purchase.repository.PurchaseRequirementMapper;
import com.zhul.erp.modules.sales.constants.SalesConstants;
import com.zhul.erp.modules.sales.entity.SalesOrderDO;
import com.zhul.erp.modules.sales.entity.SalesOrderItemDO;
import com.zhul.erp.modules.sales.repository.SalesOrderItemMapper;
import com.zhul.erp.modules.sales.repository.SalesOrderMapper;
import com.zhul.erp.modules.sales.support.OrderProgress;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 订单型号进度的「待采购 ↔ 已下单」由采购单驱动：全部数量都在已下单的采购单上为已下单，否则待采购。
 * 只处理有采购需求、当前进度为这两步之一的型号；已手动推进到后面的不回退。在调用方事务内执行。
 */
@Component
@RequiredArgsConstructor
public class OrderPurchaseProgress {

    private final SalesOrderItemMapper soItemMapper;
    private final SalesOrderMapper soMapper;
    private final PurchaseRequirementMapper requirementMapper;
    private final RequirementQty qty;
    private final OrderProgress progress;

    public void sync(Collection<Long> soItemIds) {
        List<Long> ids = soItemIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return;
        }
        List<OrderProgress.Step> ordered = progress.ordered();
        boolean orderedEnabled = ordered.stream().anyMatch(s -> s.enabled() && SalesConstants.PROGRESS_ORDERED.equals(s.code()));
        if (!orderedEnabled) {
            return;
        }
        Set<Long> tracked = requirementMapper.selectList(new LambdaQueryWrapper<PurchaseRequirementDO>()
                        .select(PurchaseRequirementDO::getSoItemId)
                        .in(PurchaseRequirementDO::getSoItemId, ids)
                        .isNull(PurchaseRequirementDO::getDeletedAt))
                .stream().map(PurchaseRequirementDO::getSoItemId).collect(Collectors.toSet());
        Map<Long, RequirementQty.Qty> placed = qty.bySoItem(ids);
        Set<Long> orders = new HashSet<>();
        for (SalesOrderItemDO i : soItemMapper.selectBatchIds(ids)) {
            String code = i.getProgressCode();
            if (i.getDeletedAt() != null || !tracked.contains(i.getId())
                    || !(SalesConstants.PROGRESS_PENDING.equals(code) || SalesConstants.PROGRESS_ORDERED.equals(code))) {
                continue;
            }
            int done = placed.getOrDefault(i.getId(), RequirementQty.Qty.ZERO).ordered();
            String want = done >= i.getQuantity() ? SalesConstants.PROGRESS_ORDERED : SalesConstants.PROGRESS_PENDING;
            if (!want.equals(code)) {
                i.setProgressCode(want);
                soItemMapper.updateById(i);
                orders.add(i.getSoId());
            }
        }
        for (Long soId : orders) {
            SalesOrderDO o = soMapper.selectById(soId);
            if (o == null || o.getStatus() != SalesConstants.SO_ACTIVE || SalesConstants.PROGRESS_COMPLETED.equals(o.getProgressCode())) {
                continue;
            }
            List<SalesOrderItemDO> all = soItemMapper.selectList(new LambdaQueryWrapper<SalesOrderItemDO>()
                    .eq(SalesOrderItemDO::getSoId, soId)
                    .isNull(SalesOrderItemDO::getDeletedAt));
            String code = all.stream().min(Comparator.comparingInt(x -> OrderProgress.rank(ordered, x.getProgressCode())))
                    .map(SalesOrderItemDO::getProgressCode).orElse(SalesConstants.PROGRESS_PENDING);
            if (!code.equals(o.getProgressCode())) {
                o.setProgressCode(code);
                soMapper.updateById(o);
            }
        }
    }
}
