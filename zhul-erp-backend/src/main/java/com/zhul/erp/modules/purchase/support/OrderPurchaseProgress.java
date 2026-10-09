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
 * 订单型号进度的「已出运 / 已交货代」由出运单与出库单驱动（出运数、交货代数达到型号数量），其次
 * 「待采购 / 已下单 / 已入库」由采购与入库驱动：合格入库数量（含折价接收）达到型号数量为已入库，
 * 否则全部数量都在已下单的采购单上为已下单，否则待采购；字典里停用的步骤跳过。
 * 只处理当前进度为这五步之一的型号；没有出库记录的型号只看采购与入库（系统外采购的不处理），
 * 以前手动推进到「已交货代」「已出运」、没有出库记录的不回退。已手动推进到后面的不回退。在调用方事务内执行。
 */
@Component
@RequiredArgsConstructor
public class OrderPurchaseProgress {

    private final SalesOrderItemMapper soItemMapper;
    private final SalesOrderMapper soMapper;
    private final PurchaseRequirementMapper requirementMapper;
    private final RequirementQty qty;
    private final OrderProgress progress;
    private final com.zhul.erp.modules.warehouse.support.ReceivingQty receivingQty;
    private final com.zhul.erp.modules.logistics.support.LogisticsQty logisticsQty;

    /** 由采购、入库、出库与出运自动推进的步骤 */
    public static final Set<String> AUTO = Set.of(SalesConstants.PROGRESS_PENDING, SalesConstants.PROGRESS_ORDERED,
            SalesConstants.PROGRESS_RECEIVED, SalesConstants.PROGRESS_TO_FORWARDER, SalesConstants.PROGRESS_SHIPPED);

    private static boolean enabled(List<OrderProgress.Step> ordered, String code) {
        return ordered.stream().anyMatch(s -> s.enabled() && code.equals(s.code()));
    }

    public void sync(Collection<Long> soItemIds) {
        List<Long> ids = soItemIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return;
        }
        List<OrderProgress.Step> ordered = progress.ordered();
        boolean orderedEnabled = enabled(ordered, SalesConstants.PROGRESS_ORDERED);
        boolean receivedEnabled = enabled(ordered, SalesConstants.PROGRESS_RECEIVED);
        boolean forwarderEnabled = enabled(ordered, SalesConstants.PROGRESS_TO_FORWARDER);
        boolean shippedEnabled = enabled(ordered, SalesConstants.PROGRESS_SHIPPED);
        Set<Long> tracked = requirementMapper.selectList(new LambdaQueryWrapper<PurchaseRequirementDO>()
                        .select(PurchaseRequirementDO::getSoItemId)
                        .in(PurchaseRequirementDO::getSoItemId, ids)
                        .isNull(PurchaseRequirementDO::getDeletedAt))
                .stream().map(PurchaseRequirementDO::getSoItemId).collect(Collectors.toSet());
        Map<Long, RequirementQty.Qty> placed = qty.bySoItem(ids);
        Map<Long, Integer> received = receivingQty.qualifiedBySoItem(ids);
        Map<Long, com.zhul.erp.modules.logistics.support.LogisticsQty.Qty> out = logisticsQty.bySoItem(ids);
        Set<Long> orders = new HashSet<>();
        for (SalesOrderItemDO i : soItemMapper.selectBatchIds(ids)) {
            String code = i.getProgressCode();
            boolean isTracked = tracked.contains(i.getId());
            boolean hasOutbound = out.containsKey(i.getId());
            boolean afterReceived = SalesConstants.PROGRESS_TO_FORWARDER.equals(code) || SalesConstants.PROGRESS_SHIPPED.equals(code);
            if (i.getDeletedAt() != null || !AUTO.contains(code) || (!hasOutbound && (!isTracked || afterReceived))) {
                continue;
            }
            var o = out.getOrDefault(i.getId(), com.zhul.erp.modules.logistics.support.LogisticsQty.Qty.ZERO);
            String want;
            if (o.shipped() >= i.getQuantity() && shippedEnabled) {
                want = SalesConstants.PROGRESS_SHIPPED;
            } else if (o.handed() + o.shipped() >= i.getQuantity() && forwarderEnabled) {
                want = SalesConstants.PROGRESS_TO_FORWARDER;
            } else if (!isTracked) {
                // 系统外采购的型号：没交货代时回到待采购（采购与入库在系统外）
                want = SalesConstants.PROGRESS_PENDING;
            } else {
                boolean allOrdered = placed.getOrDefault(i.getId(), RequirementQty.Qty.ZERO).ordered() >= i.getQuantity();
                boolean allReceived = received.getOrDefault(i.getId(), 0) >= i.getQuantity();
                if (allReceived && receivedEnabled) {
                    want = SalesConstants.PROGRESS_RECEIVED;
                } else if (allOrdered || allReceived) {
                    // 「已下单」停用时保持原样（与第①期一致）
                    want = orderedEnabled ? SalesConstants.PROGRESS_ORDERED : code;
                } else {
                    want = SalesConstants.PROGRESS_PENDING;
                }
            }
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
