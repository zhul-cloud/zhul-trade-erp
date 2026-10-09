package com.zhul.erp.modules.warehouse.support;

import com.zhul.erp.modules.warehouse.dto.IdQty;
import com.zhul.erp.modules.warehouse.repository.ReceivingQtyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/** 采购单行的已发、合格入库数量，订单型号行的合格入库数量（按发货、入库、差异实时汇总） */
@Component
@RequiredArgsConstructor
public class ReceivingQty {

    private final ReceivingQtyMapper mapper;

    public Map<Long, Integer> shippedByPoItem(Collection<Long> ids) {
        return query(ids, mapper::shippedByPoItem);
    }

    public Map<Long, Integer> qualifiedByPoItem(Collection<Long> ids) {
        return query(ids, mapper::qualifiedByPoItem);
    }

    public Map<Long, Integer> qualifiedBySoItem(Collection<Long> ids) {
        return query(ids, mapper::qualifiedBySoItem);
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
