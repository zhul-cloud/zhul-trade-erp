package com.zhul.erp.modules.logistics.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhul.erp.modules.logistics.constants.LogisticsConstants;
import com.zhul.erp.modules.logistics.entity.FreightAllocationDO;
import com.zhul.erp.modules.logistics.entity.OutboundBoxDO;
import com.zhul.erp.modules.logistics.entity.OutboundBoxItemDO;
import com.zhul.erp.modules.logistics.entity.OutboundOrderItemDO;
import com.zhul.erp.modules.logistics.repository.FreightAllocationMapper;
import com.zhul.erp.modules.logistics.repository.OutboundBoxItemMapper;
import com.zhul.erp.modules.logistics.repository.OutboundBoxMapper;
import com.zhul.erp.modules.logistics.repository.OutboundOrderItemMapper;
import com.zhul.erp.modules.logistics.support.FreightAllocator;
import com.zhul.erp.modules.sales.support.PiStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 运费分摊落库（调用方事务内）：作废这笔运费原来的分摊行，按 {@link FreightAllocator} 重新写入。
 * 国内快递按出库单的计费重之和先分到出库单（体积系数 5000）；国际运费直接按箱（体积系数取货代主数据）。
 */
@Service
@RequiredArgsConstructor
public class FreightService {

    private final FreightAllocationMapper allocationMapper;
    private final OutboundBoxMapper boxMapper;
    private final OutboundBoxItemMapper boxItemMapper;
    private final OutboundOrderItemMapper itemMapper;

    public void allocateDomestic(Long waybillId, BigDecimal freight, Collection<Long> outboundIds) {
        clear(LogisticsConstants.FREIGHT_DOMESTIC, waybillId);
        if (outboundIds.isEmpty()) {
            return;
        }
        write(LogisticsConstants.FREIGHT_DOMESTIC, waybillId,
                FreightAllocator.byOutbound(freight, boxes(outboundIds, LogisticsConstants.DOMESTIC_DIVISOR)));
    }

    public void allocateInternational(Long logisticsId, BigDecimal freight, Collection<Long> outboundIds, BigDecimal divisor) {
        clear(LogisticsConstants.FREIGHT_INTERNATIONAL, logisticsId);
        if (outboundIds.isEmpty() || freight == null) {
            return;
        }
        write(LogisticsConstants.FREIGHT_INTERNATIONAL, logisticsId, FreightAllocator.toLines(freight, boxes(outboundIds, divisor)));
    }

    public void clear(int sourceType, Long sourceId) {
        allocationMapper.update(null, new LambdaUpdateWrapper<FreightAllocationDO>()
                .set(FreightAllocationDO::getDeletedAt, LocalDateTime.now())
                .eq(FreightAllocationDO::getSourceType, sourceType)
                .eq(FreightAllocationDO::getSourceId, sourceId)
                .isNull(FreightAllocationDO::getDeletedAt));
    }

    /** 某笔运费分到各出库单的金额 */
    public Map<Long, BigDecimal> byOutbound(int sourceType, Long sourceId) {
        return allocationMapper.selectList(live(sourceType, List.of(sourceId)))
                .stream().collect(Collectors.groupingBy(FreightAllocationDO::getOutboundId,
                        Collectors.reducing(BigDecimal.ZERO, FreightAllocationDO::getAmount, BigDecimal::add)));
    }

    /** 某笔运费分到各箱的金额 */
    public Map<Long, BigDecimal> byBox(int sourceType, Long sourceId) {
        return allocationMapper.selectList(live(sourceType, List.of(sourceId)))
                .stream().collect(Collectors.groupingBy(FreightAllocationDO::getBoxId,
                        Collectors.reducing(BigDecimal.ZERO, FreightAllocationDO::getAmount, BigDecimal::add)));
    }

    /** 出库单各箱的计费重（两位小数，展示用） */
    public static BigDecimal chargeable(OutboundBoxDO b, BigDecimal divisor) {
        return FreightAllocator.chargeable(b.getLength(), b.getWidth(), b.getHeight(), b.getGrossWeight(), divisor)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private List<FreightAllocator.Box> boxes(Collection<Long> outboundIds, BigDecimal divisor) {
        List<OutboundBoxDO> boxes = boxMapper.selectList(new LambdaQueryWrapper<OutboundBoxDO>()
                .in(OutboundBoxDO::getOutboundId, outboundIds)
                .isNull(OutboundBoxDO::getDeletedAt)
                .orderByAsc(OutboundBoxDO::getOutboundId, OutboundBoxDO::getBoxNo));
        if (boxes.isEmpty()) {
            return List.of();
        }
        Map<Long, List<OutboundBoxItemDO>> contents = boxItemMapper.selectList(new LambdaQueryWrapper<OutboundBoxItemDO>()
                        .in(OutboundBoxItemDO::getBoxId, boxes.stream().map(OutboundBoxDO::getId).toList())
                        .isNull(OutboundBoxItemDO::getDeletedAt))
                .stream().collect(Collectors.groupingBy(OutboundBoxItemDO::getBoxId));
        Map<Long, OutboundOrderItemDO> items = new HashMap<>();
        itemMapper.selectList(new LambdaQueryWrapper<OutboundOrderItemDO>()
                        .in(OutboundOrderItemDO::getOutboundId, outboundIds)
                        .isNull(OutboundOrderItemDO::getDeletedAt))
                .forEach(i -> items.put(i.getId(), i));
        List<FreightAllocator.Box> out = new ArrayList<>(boxes.size());
        for (OutboundBoxDO b : boxes) {
            Map<Long, BigDecimal> values = new LinkedHashMap<>();
            for (OutboundBoxItemDO bi : contents.getOrDefault(b.getId(), List.of())) {
                OutboundOrderItemDO i = items.get(bi.getOutboundItemId());
                if (i != null) {
                    values.merge(i.getSoItemId(), i.getUnitPriceCny().multiply(BigDecimal.valueOf(bi.getQuantity())), BigDecimal::add);
                }
            }
            out.add(new FreightAllocator.Box(b.getId(), b.getOutboundId(),
                    FreightAllocator.chargeable(b.getLength(), b.getWidth(), b.getHeight(), b.getGrossWeight(), divisor), values));
        }
        return out;
    }

    private void write(int sourceType, Long sourceId, List<FreightAllocator.Share> shares) {
        for (FreightAllocator.Share s : shares) {
            FreightAllocationDO a = new FreightAllocationDO();
            a.setTenantId(PiStore.tenantId());
            a.setSourceType(sourceType);
            a.setSourceId(sourceId);
            a.setOutboundId(s.outboundId());
            a.setBoxId(s.boxId());
            a.setSoItemId(s.soItemId());
            a.setAmount(s.amount());
            allocationMapper.insert(a);
        }
    }

    private LambdaQueryWrapper<FreightAllocationDO> live(int sourceType, Collection<Long> sourceIds) {
        return new LambdaQueryWrapper<FreightAllocationDO>()
                .eq(FreightAllocationDO::getSourceType, sourceType)
                .in(FreightAllocationDO::getSourceId, sourceIds)
                .isNull(FreightAllocationDO::getDeletedAt);
    }
}
