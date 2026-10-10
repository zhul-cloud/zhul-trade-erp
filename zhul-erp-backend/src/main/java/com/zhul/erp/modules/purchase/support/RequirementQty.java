package com.zhul.erp.modules.purchase.support;

import com.zhul.erp.modules.purchase.dto.QtyRow;
import com.zhul.erp.modules.purchase.entity.PurchaseRequirementDO;
import com.zhul.erp.modules.purchase.repository.PurchaseOrderItemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 需求、订单型号行在采购单上的数量：草稿中、已下单（不落库，按采购单行实时汇总） */
@Component
@RequiredArgsConstructor
public class RequirementQty {

    private final PurchaseOrderItemMapper itemMapper;

    public record Qty(int draft, int ordered) {
        public static final Qty ZERO = new Qty(0, 0);

        public int placed() {
            return draft + ordered;
        }
    }

    public Map<Long, Qty> byRequirement(Collection<Long> ids) {
        return toMap(ids == null || ids.isEmpty() ? List.of() : itemMapper.qtyByRequirement(ids.stream().distinct().toList()));
    }

    public Map<Long, Qty> bySoItem(Collection<Long> ids) {
        return toMap(ids == null || ids.isEmpty() ? List.of() : itemMapper.qtyBySoItem(ids.stream().distinct().toList()));
    }

    public Qty of(Long requirementId) {
        return byRequirement(List.of(requirementId)).getOrDefault(requirementId, Qty.ZERO);
    }

    /** 可下单数量 = 需求数量 − 草稿中 − 已下单 */
    public static int available(PurchaseRequirementDO r, Qty q) {
        return Math.max(0, r.getQuantity() - q.placed());
    }

    private static Map<Long, Qty> toMap(List<QtyRow> rows) {
        Map<Long, Qty> map = new HashMap<>(rows.size() * 2 + 1);
        for (QtyRow r : rows) {
            map.put(r.getId(), new Qty(Objects.requireNonNullElse(r.getDraftQty(), 0), Objects.requireNonNullElse(r.getOrderedQty(), 0)));
        }
        return map;
    }
}
