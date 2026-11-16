package com.zhul.erp.modules.logistics.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhul.erp.modules.logistics.constants.LogisticsConstants;
import com.zhul.erp.modules.logistics.entity.FreightAllocationDO;
import com.zhul.erp.modules.logistics.entity.OutboundBoxDO;
import com.zhul.erp.modules.logistics.entity.OutboundBoxItemDO;
import com.zhul.erp.modules.logistics.entity.OutboundOrderItemDO;
import com.zhul.erp.modules.logistics.repository.FreightAllocationMapper;
import com.zhul.erp.modules.logistics.repository.OutboundOrderItemMapper;
import com.zhul.erp.modules.logistics.support.FreightAllocator;
import com.zhul.erp.modules.logistics.support.OutboundBoxes;
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
 * 国内快递（体积系数 5000）与国际运费（体积系数取货代主数据）都先按箱计费重分到箱，再按箱内货值分到行；
 * 合并打包的箱子装有不同出库单的货，行带所属出库单（openspec outbound-merge-pack）。
 */
@Service
@RequiredArgsConstructor
public class FreightService {

    private final FreightAllocationMapper allocationMapper;
    private final OutboundBoxes outboundBoxes;
    private final OutboundOrderItemMapper itemMapper;

    public void allocateDomestic(Long waybillId, BigDecimal freight, Collection<Long> outboundIds) {
        clear(LogisticsConstants.FREIGHT_DOMESTIC, waybillId);
        if (outboundIds.isEmpty()) {
            return;
        }
        write(LogisticsConstants.FREIGHT_DOMESTIC, waybillId,
                FreightAllocator.toLines(freight, boxes(outboundIds, LogisticsConstants.DOMESTIC_DIVISOR)));
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

    /** 某笔运费分到各销售出库单的金额 */
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

    /** 销售出库单各箱的计费重（两位小数，展示用） */
    public static BigDecimal chargeable(OutboundBoxDO b, BigDecimal divisor) {
        return FreightAllocator.chargeable(b.getLength(), b.getWidth(), b.getHeight(), b.getGrossWeight(), divisor)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /** 这些出库单的箱子（合并打包的整组箱子只算一次），箱内按「出库单 + 订单型号行」合并货值 */
    private List<FreightAllocator.Box> boxes(Collection<Long> outboundIds, BigDecimal divisor) {
        List<OutboundBoxDO> boxes = outboundBoxes.distinct(outboundIds);
        if (boxes.isEmpty()) {
            return List.of();
        }
        Map<Long, List<OutboundBoxItemDO>> contents = outboundBoxes.contents(boxes.stream().map(OutboundBoxDO::getId).toList());
        List<Long> itemIds = contents.values().stream().flatMap(List::stream).map(OutboundBoxItemDO::getOutboundItemId).distinct().toList();
        Map<Long, OutboundOrderItemDO> items = new HashMap<>();
        if (!itemIds.isEmpty()) {
            itemMapper.selectBatchIds(itemIds).forEach(i -> items.put(i.getId(), i));
        }
        List<FreightAllocator.Box> out = new ArrayList<>(boxes.size());
        for (OutboundBoxDO b : boxes) {
            Map<List<Long>, BigDecimal> values = new LinkedHashMap<>();
            for (OutboundBoxItemDO bi : contents.getOrDefault(b.getId(), List.of())) {
                OutboundOrderItemDO i = items.get(bi.getOutboundItemId());
                if (i != null) {
                    values.merge(List.of(i.getOutboundId(), i.getSoItemId()),
                            i.getUnitPriceCny().multiply(BigDecimal.valueOf(bi.getQuantity())), BigDecimal::add);
                }
            }
            List<FreightAllocator.Line> lines = values.entrySet().stream()
                    .map(e -> new FreightAllocator.Line(e.getKey().get(0), e.getKey().get(1), e.getValue())).toList();
            out.add(new FreightAllocator.Box(b.getId(),
                    FreightAllocator.chargeable(b.getLength(), b.getWidth(), b.getHeight(), b.getGrossWeight(), divisor), lines));
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
