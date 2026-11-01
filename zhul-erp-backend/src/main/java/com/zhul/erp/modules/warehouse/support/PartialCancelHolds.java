package com.zhul.erp.modules.warehouse.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.purchase.entity.PurchaseOrderItemDO;
import com.zhul.erp.modules.purchase.support.PurchaseCalc;
import com.zhul.erp.modules.purchase.support.PurchaseLogs;
import com.zhul.erp.modules.sales.entity.SalesOrderDO;
import com.zhul.erp.modules.sales.entity.SalesOrderItemDO;
import com.zhul.erp.modules.sales.repository.SalesOrderItemMapper;
import com.zhul.erp.modules.warehouse.constants.WarehouseConstants;
import com.zhul.erp.modules.warehouse.entity.PurchaseReceiptDO;
import com.zhul.erp.modules.warehouse.entity.PurchaseReceiptItemDO;
import com.zhul.erp.modules.warehouse.entity.StockHoldDO;
import com.zhul.erp.modules.warehouse.repository.StockHoldMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 订单部分取消产生的闲置库存（spec warehouse/stock-hold「闲置库存」）：取消时已在仓的数量，
 * 以及之后到货验收时超出订单型号数量的合格数量。转入后不再算订单型号的合格入库数量（见 ReceivingQtyMapper）。
 * 成本单价取来源采购订单行的不含税人民币单价，HALF_UP 两位。在调用方事务内执行。
 */
@Component
@RequiredArgsConstructor
public class PartialCancelHolds {

    private final StockHoldMapper holdMapper;
    private final SalesOrderItemMapper soItemMapper;
    private final ReceivingQty receivingQty;
    private final PurchaseLogs logs;

    /** 部分取消时已在仓的数量；src 为合格入库最多的采购订单行（取成本） */
    public StockHoldDO atCancel(SalesOrderDO o, SalesOrderItemDO item, int quantity, PurchaseOrderItemDO src) {
        StockHoldDO h = newHold(item, quantity, src);
        h.setSoId(o.getId());
        h.setReceiptId(0L);
        h.setHandleNote("");
        holdMapper.insert(h);
        return h;
    }

    /**
     * 验收入库后：部分取消过的型号，合格入库（已扣除转闲置的）超过订单型号数量的部分转入闲置库存，
     * 每个入库行最多转本行的合格数量。返回转入的件数（按订单型号行）。
     */
    public Map<Long, Integer> afterReceipt(PurchaseReceiptDO r, List<PurchaseReceiptItemDO> items, Map<Long, PurchaseOrderItemDO> poItems,
                                           Long operatorId) {
        List<Long> soItemIds = items.stream().map(PurchaseReceiptItemDO::getSoItemId).filter(x -> x != null && x > 0).distinct().toList();
        Map<Long, Integer> out = new HashMap<>();
        if (soItemIds.isEmpty()) {
            return out;
        }
        Map<Long, SalesOrderItemDO> soItems = new HashMap<>();
        soItemMapper.selectBatchIds(soItemIds).stream()
                .filter(i -> i.getDeletedAt() == null && Objects.requireNonNullElse(i.getCancelledQuantity(), 0) > 0)
                .forEach(i -> soItems.put(i.getId(), i));
        if (soItems.isEmpty()) {
            return out;
        }
        Map<Long, Integer> qualified = new HashMap<>(receivingQty.qualifiedBySoItem(soItems.keySet()));
        List<String> text = new ArrayList<>();
        for (PurchaseReceiptItemDO x : items) {
            SalesOrderItemDO i = soItems.get(x.getSoItemId());
            if (i == null) {
                continue;
            }
            int excess = qualified.getOrDefault(i.getId(), 0) - i.getQuantity();
            int mine = Math.min(Objects.requireNonNullElse(x.getQualifiedQty(), 0), Objects.requireNonNullElse(x.getShippedQty(), 0));
            int qty = Math.min(excess, mine);
            if (qty <= 0) {
                continue;
            }
            StockHoldDO h = newHold(i, qty, poItems.get(x.getPoItemId()));
            h.setSoId(i.getSoId());
            h.setReceiptId(r.getId());
            h.setHandleNote("");
            holdMapper.insert(h);
            qualified.merge(i.getId(), -qty, Integer::sum);
            out.merge(i.getId(), qty, Integer::sum);
            text.add(i.getModel() + " × " + qty);
        }
        if (!text.isEmpty()) {
            logs.add(r.getPoId(), "转入闲置库存", r.getGrNo() + "：来源订单已部分取消，超出订单需要的合格数量转入闲置库存："
                    + String.join("、", text), operatorId);
        }
        return out;
    }

    /** 冲销入库：这张入库单转入的闲置库存还暂存中的一并撤回（软删除），已处置的不能冲销 */
    public void voidForReceipt(Long receiptId) {
        List<StockHoldDO> rows = holdMapper.selectList(new LambdaQueryWrapper<StockHoldDO>()
                .eq(StockHoldDO::getReceiptId, receiptId)
                .eq(StockHoldDO::getSourceType, WarehouseConstants.HOLD_SRC_PARTIAL_CANCEL)
                .isNull(StockHoldDO::getDeletedAt));
        for (StockHoldDO h : rows) {
            if (h.getStatus() != WarehouseConstants.HOLD_ACTIVE) {
                throw new BizException("这张入库单转入的闲置库存已处置，不能冲销");
            }
        }
        for (StockHoldDO h : rows) {
            h.setDeletedAt(LocalDateTime.now());
            holdMapper.updateById(h);
        }
    }

    private static StockHoldDO newHold(SalesOrderItemDO item, int quantity, PurchaseOrderItemDO src) {
        StockHoldDO h = new StockHoldDO();
        h.setTenantId(item.getTenantId());
        h.setSourceType(WarehouseConstants.HOLD_SRC_PARTIAL_CANCEL);
        h.setDiscrepancyId(0L);
        h.setPoId(src == null ? 0L : src.getPoId());
        h.setSoItemId(item.getId());
        h.setModel(item.getModel());
        h.setBrand(Objects.requireNonNullElse(item.getBrand(), ""));
        h.setCategory(Objects.requireNonNullElse(item.getCategory(), ""));
        h.setQuantity(quantity);
        h.setCostPrice(src == null || src.getNetPriceCny() == null ? BigDecimal.ZERO.setScale(2) : PurchaseCalc.money(src.getNetPriceCny()));
        h.setLocationNote("");
        h.setStatus(WarehouseConstants.HOLD_ACTIVE);
        h.setReturnCarrier("");
        h.setReturnTrackingNo("");
        return h;
    }
}
