package com.zhul.erp.modules.logistics.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.modules.logistics.entity.OutboundBoxDO;
import com.zhul.erp.modules.logistics.entity.OutboundBoxItemDO;
import com.zhul.erp.modules.logistics.entity.OutboundOrderDO;
import com.zhul.erp.modules.logistics.repository.OutboundBoxItemMapper;
import com.zhul.erp.modules.logistics.repository.OutboundBoxMapper;
import com.zhul.erp.modules.logistics.repository.OutboundOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 取销售出库单的箱子（openspec outbound-merge-pack）：单独打包的取自己的箱子（pack_id 为空、outbound_id 为本单），
 * 合并打包的取整组的箱子（按 pack_id）。同一组的箱子只返回一次。按 outbound_id 直接查箱子会漏掉合并打包组里其他单的箱子，
 * 取箱一律走这里。
 */
@Component
@RequiredArgsConstructor
public class OutboundBoxes {

    private final OutboundOrderMapper outboundMapper;
    private final OutboundBoxMapper boxMapper;
    private final OutboundBoxItemMapper boxItemMapper;

    /** 出库单 → 它的箱子：单独打包为自己的，合并打包为整组的（按箱号） */
    public Map<Long, List<OutboundBoxDO>> byOutbound(Collection<OutboundOrderDO> obs) {
        if (obs.isEmpty()) {
            return Map.of();
        }
        List<OutboundBoxDO> rows = query(obs);
        Map<Long, List<OutboundBoxDO>> single = rows.stream().filter(b -> b.getPackId() == null)
                .collect(Collectors.groupingBy(OutboundBoxDO::getOutboundId));
        Map<Long, List<OutboundBoxDO>> packed = rows.stream().filter(b -> b.getPackId() != null)
                .collect(Collectors.groupingBy(OutboundBoxDO::getPackId));
        Map<Long, List<OutboundBoxDO>> out = new HashMap<>(obs.size() * 2);
        for (OutboundOrderDO o : obs) {
            out.put(o.getId(), o.getPackId() == null ? single.getOrDefault(o.getId(), List.of())
                    : packed.getOrDefault(o.getPackId(), List.of()));
        }
        return out;
    }

    /** 这些出库单涉及的全部箱子，每箱只出现一次（按出库单、箱号排序） */
    public List<OutboundBoxDO> distinct(Collection<Long> outboundIds) {
        if (outboundIds.isEmpty()) {
            return List.of();
        }
        return query(outboundMapper.selectBatchIds(outboundIds));
    }

    /** 箱内明细：箱子 → 行 */
    public Map<Long, List<OutboundBoxItemDO>> contents(Collection<Long> boxIds) {
        if (boxIds.isEmpty()) {
            return Map.of();
        }
        return boxItemMapper.selectList(new LambdaQueryWrapper<OutboundBoxItemDO>()
                        .in(OutboundBoxItemDO::getBoxId, boxIds)
                        .isNull(OutboundBoxItemDO::getDeletedAt))
                .stream().collect(Collectors.groupingBy(OutboundBoxItemDO::getBoxId));
    }

    private List<OutboundBoxDO> query(Collection<OutboundOrderDO> obs) {
        List<Long> single = obs.stream().filter(o -> o.getPackId() == null).map(OutboundOrderDO::getId).toList();
        Set<Long> packs = obs.stream().map(OutboundOrderDO::getPackId).filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (single.isEmpty() && packs.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<OutboundBoxDO> w = new LambdaQueryWrapper<OutboundBoxDO>()
                .isNull(OutboundBoxDO::getDeletedAt)
                .and(x -> {
                    if (!single.isEmpty()) {
                        x.nested(y -> y.isNull(OutboundBoxDO::getPackId).in(OutboundBoxDO::getOutboundId, single));
                    }
                    if (!single.isEmpty() && !packs.isEmpty()) {
                        x.or();
                    }
                    if (!packs.isEmpty()) {
                        x.in(OutboundBoxDO::getPackId, packs);
                    }
                })
                .orderByAsc(OutboundBoxDO::getOutboundId, OutboundBoxDO::getBoxNo);
        return boxMapper.selectList(w);
    }
}
