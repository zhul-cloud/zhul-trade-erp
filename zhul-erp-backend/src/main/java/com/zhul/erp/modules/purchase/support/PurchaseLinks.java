package com.zhul.erp.modules.purchase.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.modules.purchase.constants.PurchaseConstants;
import com.zhul.erp.modules.purchase.entity.PurchaseOrderDO;
import com.zhul.erp.modules.purchase.entity.PurchaseOrderItemDO;
import com.zhul.erp.modules.purchase.entity.PurchaseRequirementDO;
import com.zhul.erp.modules.purchase.repository.PurchaseOrderItemMapper;
import com.zhul.erp.modules.purchase.repository.PurchaseOrderMapper;
import com.zhul.erp.modules.purchase.repository.PurchaseRequirementMapper;
import com.zhul.erp.modules.sales.support.PiStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 销售订单读取采购信息：型号行的采购员、已下单数量与采购单（只读） */
@Component
@RequiredArgsConstructor
public class PurchaseLinks {

    private final PurchaseRequirementMapper requirementMapper;
    private final PurchaseOrderItemMapper itemMapper;
    private final PurchaseOrderMapper orderMapper;
    private final RequirementQty qty;
    private final com.zhul.erp.modules.warehouse.support.ReceivingQty receivingQty;
    private final com.zhul.erp.modules.logistics.support.LogisticsQty logisticsQty;

    public record PoRef(Long id, String poNo, Integer status, java.time.LocalDate expectedShipDate) {
    }

    /** 在途发货单上的数量 */
    public record Transit(Long shipmentId, String sdNo, String carrier, int quantity, java.time.LocalDate expectedArrival) {
    }

    /**
     * tracked：有采购需求（存量中进度已过「待采购」的型号没有）；received：合格入库数量（含折价接收）；
     * shipped：已发数量；transits：在途发货单
     */
    public record ItemPurchase(boolean tracked, Set<Long> purchaserIds, int ordered, int draft, int received, List<PoRef> orders,
                               int shipped, List<Transit> transits, int handedOut, int shippedOut) {

        /** 货物状态（件数，quantity 为型号数量）：已出运、已交货代、在仓、在途、待发货、待采购 */
        public Goods goods(int quantity) {
            int inTransit = transits.stream().mapToInt(Transit::quantity).sum();
            int receivedQty = Math.min(received, quantity);
            return new Goods(receivedQty, inTransit, Math.max(0, ordered - shipped), Math.max(0, quantity - ordered),
                    shippedOut, handedOut, Math.max(0, receivedQty - handedOut - shippedOut));
        }
    }

    public Map<Long, ItemPurchase> forSoItems(Collection<Long> soItemIds) {
        List<Long> ids = soItemIds.stream().filter(Objects::nonNull).distinct().toList();
        Map<Long, ItemPurchase> out = new HashMap<>(ids.size() * 2 + 1);
        if (ids.isEmpty()) {
            return out;
        }
        Map<Long, Set<Long>> purchasers = new HashMap<>();
        Set<Long> tracked = new LinkedHashSet<>();
        for (PurchaseRequirementDO r : requirementMapper.selectList(new LambdaQueryWrapper<PurchaseRequirementDO>()
                .in(PurchaseRequirementDO::getSoItemId, ids)
                .isNull(PurchaseRequirementDO::getDeletedAt)
                .orderByAsc(PurchaseRequirementDO::getId))) {
            tracked.add(r.getSoItemId());
            if (r.getStatus() == PurchaseConstants.REQ_ACTIVE && r.getPurchaserId() != null) {
                purchasers.computeIfAbsent(r.getSoItemId(), k -> new LinkedHashSet<>()).add(r.getPurchaserId());
            }
        }
        Map<Long, List<PoRef>> orders = new HashMap<>();
        List<PurchaseOrderItemDO> lines = itemMapper.selectList(new LambdaQueryWrapper<PurchaseOrderItemDO>()
                .in(PurchaseOrderItemDO::getSoItemId, ids)
                .isNull(PurchaseOrderItemDO::getDeletedAt));
        Map<Long, PurchaseOrderDO> pos = new HashMap<>();
        if (!lines.isEmpty()) {
            orderMapper.selectBatchIds(lines.stream().map(PurchaseOrderItemDO::getPoId).distinct().toList()).stream()
                    .filter(p -> p.getDeletedAt() == null && p.getStatus() != PurchaseConstants.PO_CANCELLED)
                    .forEach(p -> pos.put(p.getId(), p));
        }
        for (PurchaseOrderItemDO i : lines) {
            PurchaseOrderDO p = pos.get(i.getPoId());
            if (p != null) {
                List<PoRef> refs = orders.computeIfAbsent(i.getSoItemId(), k -> new ArrayList<>());
                if (refs.stream().noneMatch(x -> x.id().equals(p.getId()))) {
                    refs.add(new PoRef(p.getId(), p.getPoNo(), p.getStatus(), p.getExpectedShipDate()));
                }
            }
        }
        Map<Long, RequirementQty.Qty> q = qty.bySoItem(ids);
        Map<Long, Integer> received = receivingQty.qualifiedBySoItem(ids);
        Map<Long, Integer> shipped = receivingQty.shippedBySoItem(ids);
        Map<Long, List<com.zhul.erp.modules.warehouse.dto.TransitRow>> transits = receivingQty.inTransitBySoItem(ids);
        Map<Long, com.zhul.erp.modules.logistics.support.LogisticsQty.Qty> outbound = logisticsQty.bySoItem(ids);
        for (Long id : ids) {
            RequirementQty.Qty x = q.getOrDefault(id, RequirementQty.Qty.ZERO);
            out.put(id, new ItemPurchase(tracked.contains(id), purchasers.getOrDefault(id, Set.of()), x.ordered(), x.draft(), received.getOrDefault(id, 0),
                    orders.getOrDefault(id, List.of()), shipped.getOrDefault(id, 0), transits.getOrDefault(id, List.of()).stream()
                    .map(t -> new Transit(t.getShipmentId(), t.getSdNo(), t.getCarrier(), t.getQuantity(), t.getExpectedArrivalDate())).toList(),
                    outbound.getOrDefault(id, com.zhul.erp.modules.logistics.support.LogisticsQty.Qty.ZERO).handed(),
                    outbound.getOrDefault(id, com.zhul.erp.modules.logistics.support.LogisticsQty.Qty.ZERO).shipped()));
        }
        return out;
    }

    /** 订单型号行的货物件数分布 */
    public record Goods(int received, int inTransit, int pendingShip, int pendingPurchase, int shippedOut, int handedOut,
                        int inWarehouse) {
    }

    /** 订单 → 有效需求的采购员（拆分给多人时都在） */
    public Map<Long, Set<Long>> purchasersByOrder(Collection<Long> soIds) {
        List<Long> ids = soIds.stream().filter(Objects::nonNull).distinct().toList();
        Map<Long, Set<Long>> out = new LinkedHashMap<>();
        if (ids.isEmpty()) {
            return out;
        }
        requirementMapper.selectList(new LambdaQueryWrapper<PurchaseRequirementDO>()
                        .select(PurchaseRequirementDO::getSoId, PurchaseRequirementDO::getPurchaserId)
                        .in(PurchaseRequirementDO::getSoId, ids)
                        .eq(PurchaseRequirementDO::getStatus, PurchaseConstants.REQ_ACTIVE)
                        .isNotNull(PurchaseRequirementDO::getPurchaserId)
                        .isNull(PurchaseRequirementDO::getDeletedAt)
                        .orderByAsc(PurchaseRequirementDO::getId))
                .forEach(r -> out.computeIfAbsent(r.getSoId(), k -> new LinkedHashSet<>()).add(r.getPurchaserId()));
        return out;
    }

    /** 采购员负责的订单（有效需求） */
    public List<Long> orderIdsByPurchaser(Long purchaserId) {
        return requirementMapper.selectList(new LambdaQueryWrapper<PurchaseRequirementDO>()
                        .select(PurchaseRequirementDO::getSoId)
                        .eq(PurchaseRequirementDO::getTenantId, PiStore.tenantId())
                        .eq(PurchaseRequirementDO::getPurchaserId, purchaserId)
                        .eq(PurchaseRequirementDO::getStatus, PurchaseConstants.REQ_ACTIVE)
                        .isNull(PurchaseRequirementDO::getDeletedAt))
                .stream().map(PurchaseRequirementDO::getSoId).distinct().toList();
    }

    /** 订单型号行的有效需求（改采购员用） */
    public List<PurchaseRequirementDO> activeRequirements(Collection<Long> soItemIds) {
        List<Long> ids = soItemIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        return requirementMapper.selectList(new LambdaQueryWrapper<PurchaseRequirementDO>()
                .in(PurchaseRequirementDO::getSoItemId, ids)
                .eq(PurchaseRequirementDO::getStatus, PurchaseConstants.REQ_ACTIVE)
                .isNull(PurchaseRequirementDO::getDeletedAt)
                .orderByAsc(PurchaseRequirementDO::getId));
    }
}
