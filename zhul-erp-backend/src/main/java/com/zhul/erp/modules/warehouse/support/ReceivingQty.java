package com.zhul.erp.modules.warehouse.support;

import com.zhul.erp.modules.warehouse.dto.IdQty;
import com.zhul.erp.modules.warehouse.repository.ReceivingQtyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import com.zhul.erp.modules.sales.entity.SalesOrderItemDO;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/** 采购订单行的已发、合格入库数量，订单型号行的合格入库数量（按发货、入库、差异实时汇总） */
@Component
@RequiredArgsConstructor
public class ReceivingQty {

    private final ReceivingQtyMapper mapper;
    private final com.zhul.erp.modules.sales.repository.SalesOrderItemMapper soItemMapper;

    public Map<Long, Integer> shippedByPoItem(Collection<Long> ids) {
        return query(ids, mapper::shippedByPoItem);
    }

    public Map<Long, Integer> qualifiedByPoItem(Collection<Long> ids) {
        return query(ids, mapper::qualifiedByPoItem);
    }

    /**
     * 订单型号行的合格入库数量。部分取消过的型号行不超过取消后的数量：取消时在仓的、之后到货超出订单需要的合格数量
     * 已释放成可用库存（openspec inventory-foundation），不再算这个型号行的（历史转入闲置库存的已在 SQL 里扣除）。
     */
    public Map<Long, Integer> qualifiedBySoItem(Collection<Long> ids) {
        Map<Long, Integer> out = query(ids, mapper::qualifiedBySoItem);
        if (out.isEmpty()) {
            return out;
        }
        for (SalesOrderItemDO i : soItemMapper.selectBatchIds(out.keySet())) {
            if (Objects.requireNonNullElse(i.getCancelledQuantity(), 0) > 0 && i.getQuantity() != null) {
                out.computeIfPresent(i.getId(), (k, v) -> Math.min(v, i.getQuantity()));
            }
        }
        return out;
    }

    public Map<Long, Integer> shippedBySoItem(Collection<Long> ids) {
        return query(ids, mapper::shippedBySoItem);
    }

    /** 订单型号行 → 它所在的在途发货单（按预计到货日期从早到晚） */
    public Map<Long, List<com.zhul.erp.modules.warehouse.dto.TransitRow>> inTransitBySoItem(Collection<Long> ids) {
        List<Long> keys = ids == null ? List.of() : ids.stream().filter(Objects::nonNull).distinct().toList();
        Map<Long, List<com.zhul.erp.modules.warehouse.dto.TransitRow>> out = new HashMap<>();
        if (!keys.isEmpty()) {
            mapper.inTransitBySoItem(keys).forEach(r -> out.computeIfAbsent(r.getSoItemId(), k -> new java.util.ArrayList<>()).add(r));
        }
        return out;
    }

    private static Map<Long, Integer> query(Collection<Long> ids, Function<List<Long>, List<IdQty>> fn) {
        List<Long> keys = ids == null ? List.of() : ids.stream().filter(Objects::nonNull).distinct().toList();
        Map<Long, Integer> out = new HashMap<>(keys.size() * 2 + 1);
        if (!keys.isEmpty()) {
            fn.apply(keys).forEach(r -> out.put(r.getId(), Objects.requireNonNullElse(r.getQty(), 0)));
        }
        return out;
    }
}
