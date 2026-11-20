package com.zhul.erp.modules.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.modules.inventory.constants.InvConstants;
import com.zhul.erp.modules.inventory.entity.InvMoveDO;
import com.zhul.erp.modules.inventory.entity.InvReservationDO;
import com.zhul.erp.modules.inventory.repository.InvMoveMapper;
import com.zhul.erp.modules.inventory.repository.InvReservationMapper;
import com.zhul.erp.modules.inventory.service.InventoryPosting.Doc;
import com.zhul.erp.modules.inventory.service.InventoryPosting.Line;
import com.zhul.erp.modules.inventory.service.InventoryPosting.Loc;
import com.zhul.erp.modules.inventory.service.InventoryPosting.Target;
import com.zhul.erp.modules.sales.constants.SalesConstants;
import com.zhul.erp.modules.sales.entity.SalesOrderDO;
import com.zhul.erp.modules.sales.entity.SalesOrderItemDO;
import com.zhul.erp.modules.sales.repository.SalesOrderItemMapper;
import com.zhul.erp.modules.sales.repository.SalesOrderMapper;
import com.zhul.erp.modules.sales.support.PiStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 订单预占（openspec inventory-foundation 决策 5）：预占挂在销售订单型号行上，代表「这批货是这个订单的」。
 * 型号行还需要的数量 = 剩余数量（订单已取消为 0）− 现有预占（自有仓与货代仓）− 已销售出库。
 */
@Service
@RequiredArgsConstructor
public class InventoryReservations {

    private final InvReservationMapper reservationMapper;
    private final InvMoveMapper moveMapper;
    private final SalesOrderItemMapper soItemMapper;
    private final SalesOrderMapper soMapper;
    private final InventoryPosting posting;

    /** 各型号行还需要预占的数量（不小于 0） */
    public Map<Long, Integer> need(Collection<Long> soItemIds) {
        List<Long> ids = soItemIds.stream().filter(Objects::nonNull).distinct().toList();
        Map<Long, Integer> out = new HashMap<>();
        if (ids.isEmpty()) {
            return out;
        }
        Map<Long, Integer> wanted = wanted(ids);
        Map<Long, Integer> reserved = reserved(ids);
        Map<Long, Integer> shipped = shipped(ids);
        for (Long id : ids) {
            out.put(id, Math.max(0, wanted.getOrDefault(id, 0) - reserved.getOrDefault(id, 0) - shipped.getOrDefault(id, 0)));
        }
        return out;
    }

    /** 型号行的剩余数量；订单已取消、已删除为 0 */
    public Map<Long, Integer> wanted(Collection<Long> soItemIds) {
        Map<Long, Integer> out = new HashMap<>();
        if (soItemIds.isEmpty()) {
            return out;
        }
        List<SalesOrderItemDO> items = soItemMapper.selectBatchIds(soItemIds);
        Map<Long, SalesOrderDO> orders = new HashMap<>();
        List<Long> soIds = items.stream().map(SalesOrderItemDO::getSoId).distinct().toList();
        if (!soIds.isEmpty()) {
            soMapper.selectBatchIds(soIds).forEach(o -> orders.put(o.getId(), o));
        }
        for (SalesOrderItemDO i : items) {
            SalesOrderDO o = orders.get(i.getSoId());
            boolean alive = i.getDeletedAt() == null && o != null && o.getDeletedAt() == null
                    && !Objects.equals(o.getStatus(), SalesConstants.SO_CANCELLED);
            out.put(i.getId(), alive ? Math.max(0, Objects.requireNonNullElse(i.getQuantity(), 0)) : 0);
        }
        return out;
    }

    /** 各型号行现有的预占合计（所有仓库） */
    public Map<Long, Integer> reserved(Collection<Long> soItemIds) {
        Map<Long, Integer> out = new HashMap<>();
        if (soItemIds.isEmpty()) {
            return out;
        }
        reservationMapper.selectList(new LambdaQueryWrapper<InvReservationDO>()
                        .eq(InvReservationDO::getTenantId, PiStore.tenantId())
                        .eq(InvReservationDO::getTargetType, InvConstants.TARGET_SO_ITEM)
                        .in(InvReservationDO::getTargetId, soItemIds)
                        .gt(InvReservationDO::getQuantity, 0))
                .forEach(r -> out.merge(r.getTargetId(), r.getQuantity(), Integer::sum));
        return out;
    }

    /** 各型号行已销售出库的数量（扣掉撤销的） */
    public Map<Long, Integer> shipped(Collection<Long> soItemIds) {
        Map<Long, Integer> out = new HashMap<>();
        if (soItemIds.isEmpty()) {
            return out;
        }
        moveMapper.selectList(new LambdaQueryWrapper<InvMoveDO>()
                        .select(InvMoveDO::getTargetId, InvMoveDO::getQuantity)
                        .eq(InvMoveDO::getTenantId, PiStore.tenantId())
                        .eq(InvMoveDO::getTargetType, InvConstants.TARGET_SO_ITEM)
                        .in(InvMoveDO::getTargetId, soItemIds)
                        .in(InvMoveDO::getBizType, List.of(InvConstants.BIZ_SALES_OUT, InvConstants.BIZ_SALES_OUT_UNDO))
                        .in(InvMoveDO::getOpType, List.of(InvConstants.OP_OUT, InvConstants.OP_IN)))
                .forEach(m -> out.merge(m.getTargetId(), -m.getQuantity(), Integer::sum));
        return out;
    }

    /**
     * 释放超出需要的预占：订单取消、部分取消、撤销交货代后调用。先释放自有仓的，再释放货代仓的；
     * 同一仓库内从最新的批次往前释放。释放后的货成为可用库存。
     */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public int trim(Collection<Long> soItemIds, Doc doc, Long ownWarehouseId) {
        List<Long> ids = soItemIds.stream().filter(Objects::nonNull).distinct().sorted().toList();
        if (ids.isEmpty()) {
            return 0;
        }
        Map<Long, Integer> wanted = wanted(ids);
        Map<Long, Integer> shipped = shipped(ids);
        List<Line> lines = new ArrayList<>();
        int total = 0;
        for (Long id : ids) {
            List<InvReservationDO> rs = new ArrayList<>(posting.reservations(Target.soItem(id)));
            int have = rs.stream().mapToInt(InvReservationDO::getQuantity).sum();
            int excess = have + shipped.getOrDefault(id, 0) - wanted.getOrDefault(id, 0);
            if (excess <= 0) {
                continue;
            }
            rs.sort(Comparator.<InvReservationDO>comparingInt(r -> Objects.equals(r.getWarehouseId(), ownWarehouseId) ? 0 : 1)
                    .thenComparing(InvReservationDO::getBatchId, Comparator.reverseOrder()));
            for (InvReservationDO r : rs) {
                if (excess <= 0) {
                    break;
                }
                int n = Math.min(excess, r.getQuantity());
                lines.add(Line.of(new Loc(r.getWarehouseId(), r.getProductId(), r.getBatchId(), r.getStockStatus()),
                        InvConstants.OP_RELEASE, n, Target.soItem(id), doc));
                excess -= n;
                total += n;
            }
        }
        posting.post(lines);
        return total;
    }

    /** 型号行 → 当前预占（按批次先进先出） */
    public Map<Long, List<InvReservationDO>> byItem(Collection<Long> soItemIds) {
        if (soItemIds.isEmpty()) {
            return Map.of();
        }
        return reservationMapper.selectList(new LambdaQueryWrapper<InvReservationDO>()
                        .eq(InvReservationDO::getTenantId, PiStore.tenantId())
                        .eq(InvReservationDO::getTargetType, InvConstants.TARGET_SO_ITEM)
                        .in(InvReservationDO::getTargetId, soItemIds)
                        .gt(InvReservationDO::getQuantity, 0)
                        .orderByAsc(InvReservationDO::getBatchId))
                .stream().collect(Collectors.groupingBy(InvReservationDO::getTargetId));
    }
}
