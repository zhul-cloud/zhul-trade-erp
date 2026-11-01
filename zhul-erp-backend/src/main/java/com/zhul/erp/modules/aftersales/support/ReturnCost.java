package com.zhul.erp.modules.aftersales.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.modules.purchase.constants.PurchaseConstants;
import com.zhul.erp.modules.purchase.entity.PurchaseOrderDO;
import com.zhul.erp.modules.purchase.entity.PurchaseOrderItemDO;
import com.zhul.erp.modules.purchase.repository.PurchaseOrderItemMapper;
import com.zhul.erp.modules.purchase.repository.PurchaseOrderMapper;
import com.zhul.erp.modules.sales.entity.SalesOrderItemDO;
import com.zhul.erp.modules.sales.repository.SalesOrderItemMapper;
import com.zhul.erp.modules.warehouse.support.ReceivingQty;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 订单型号行的原采购成本（spec finance/order-cost「售后退货冲减订单成本」）：有效采购订单行（已下单）的不含税人民币单价，
 * 按合格入库数量加权，没有合格入库时按采购数量加权；没有采购订单时取报价时的采购成本价；都没有时为 0。HALF_UP 两位。
 * 默认退回的供应商取合格入库（其次采购数量）最多的那一行的采购订单。
 */
@Component
@RequiredArgsConstructor
public class ReturnCost {

    private static final MathContext MC = MathContext.DECIMAL128;

    private final PurchaseOrderItemMapper poItemMapper;
    private final PurchaseOrderMapper poMapper;
    private final SalesOrderItemMapper soItemMapper;
    private final ReceivingQty receivingQty;

    public record Origin(BigDecimal unitCost, Long poId, String poNo, Long supplierId) {
        public static final Origin NONE = new Origin(BigDecimal.ZERO.setScale(2), null, null, null);
    }

    public Map<Long, Origin> originsOf(Collection<Long> soItemIds) {
        List<Long> ids = soItemIds.stream().filter(Objects::nonNull).distinct().toList();
        // 退货单至少有一个型号行，ids 不会为空
        Map<Long, Origin> out = new HashMap<>();
        List<PurchaseOrderItemDO> lines = poItemMapper.selectList(new LambdaQueryWrapper<PurchaseOrderItemDO>()
                .in(PurchaseOrderItemDO::getSoItemId, ids)
                .isNull(PurchaseOrderItemDO::getDeletedAt));
        Map<Long, PurchaseOrderDO> pos = lines.isEmpty() ? Map.of() : poMapper.selectBatchIds(lines.stream().map(PurchaseOrderItemDO::getPoId)
                        .distinct().toList()).stream()
                .filter(p -> p.getDeletedAt() == null && p.getStatus() == PurchaseConstants.PO_ORDERED)
                .collect(Collectors.toMap(PurchaseOrderDO::getId, p -> p));
        List<PurchaseOrderItemDO> ordered = lines.stream().filter(l -> pos.containsKey(l.getPoId()) && l.getNetPriceCny() != null).toList();
        Map<Long, Integer> qualified = receivingQty.qualifiedByPoItem(ordered.stream().map(PurchaseOrderItemDO::getId).toList());
        Map<Long, List<PurchaseOrderItemDO>> bySoItem = ordered.stream().collect(Collectors.groupingBy(PurchaseOrderItemDO::getSoItemId));
        Map<Long, SalesOrderItemDO> soItems = soItemMapper.selectBatchIds(ids).stream().collect(Collectors.toMap(SalesOrderItemDO::getId, i -> i));
        for (Long id : ids) {
            List<PurchaseOrderItemDO> mine = bySoItem.getOrDefault(id, List.of());
            if (mine.isEmpty()) {
                SalesOrderItemDO i = soItems.get(id);
                BigDecimal cost = i == null || i.getCostPrice() == null ? BigDecimal.ZERO : i.getCostPrice();
                out.put(id, new Origin(cost.setScale(2, RoundingMode.HALF_UP), null, null, null));
                continue;
            }
            boolean anyQualified = mine.stream().anyMatch(l -> qualified.getOrDefault(l.getId(), 0) > 0);
            BigDecimal weight = BigDecimal.ZERO;
            BigDecimal value = BigDecimal.ZERO;
            for (PurchaseOrderItemDO l : mine) {
                int w = anyQualified ? qualified.getOrDefault(l.getId(), 0) : l.getQuantity();
                weight = weight.add(BigDecimal.valueOf(w));
                value = value.add(l.getNetPriceCny().multiply(BigDecimal.valueOf(w), MC));
            }
            BigDecimal unit = weight.signum() == 0 ? BigDecimal.ZERO : value.divide(weight, MC);
            PurchaseOrderItemDO main = mine.stream().max(Comparator.comparingInt((PurchaseOrderItemDO l) -> qualified.getOrDefault(l.getId(), 0))
                    .thenComparingInt(PurchaseOrderItemDO::getQuantity)).orElseThrow();
            PurchaseOrderDO po = pos.get(main.getPoId());
            out.put(id, new Origin(unit.setScale(2, RoundingMode.HALF_UP), po.getId(), po.getPoNo(), po.getSupplierId()));
        }
        return out;
    }
}
