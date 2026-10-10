package com.zhul.erp.modules.logistics.support;

import com.zhul.erp.modules.logistics.dto.OutboundQtyRow;
import com.zhul.erp.modules.logistics.repository.LogisticsQtyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 订单型号行的出库数量：占用、已交货代（未出运）、已出运 */
@Component
@RequiredArgsConstructor
public class LogisticsQty {

    private final LogisticsQtyMapper mapper;

    public record Qty(int occupied, int handed, int shipped) {
        public static final Qty ZERO = new Qty(0, 0, 0);
    }

    public Map<Long, Qty> bySoItem(Collection<Long> ids) {
        List<Long> keys = ids == null ? List.of() : ids.stream().filter(Objects::nonNull).distinct().toList();
        Map<Long, Qty> out = new HashMap<>(keys.size() * 2 + 1);
        if (!keys.isEmpty()) {
            for (OutboundQtyRow r : mapper.bySoItem(keys)) {
                out.put(r.getId(), new Qty(nz(r.getOccupied()), nz(r.getHanded()), nz(r.getShipped())));
            }
        }
        return out;
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }
}
