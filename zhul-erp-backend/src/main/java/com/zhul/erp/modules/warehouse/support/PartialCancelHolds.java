package com.zhul.erp.modules.warehouse.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.purchase.support.PurchaseLogs;
import com.zhul.erp.modules.warehouse.constants.WarehouseConstants;
import com.zhul.erp.modules.warehouse.entity.PurchaseReceiptDO;
import com.zhul.erp.modules.warehouse.entity.StockHoldDO;
import com.zhul.erp.modules.warehouse.repository.StockHoldMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 历史上订单部分取消转入的闲置库存（spec warehouse/stock-hold，openspec inventory-foundation 起不再新增，
 * 部分取消改为释放库存预占，见 InventoryHooks.orderReduced）。这里只保留冲销入库时撤回这些历史记录，在调用方事务内执行。
 */
@Component
@RequiredArgsConstructor
public class PartialCancelHolds {

    private final StockHoldMapper holdMapper;
    private final ReceivingQty receivingQty;
    private final PurchaseLogs logs;

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

    /**
     * 冲销入库后（本单与本单转入的闲置库存已撤回）：订单型号的合格入库少于部分取消时转入闲置库存的数量，说明这部分货已不在仓，
     * 按差额撤回取消时转入、还暂存中的闲置库存（从最近转入的开始，整条软删除或减少数量），订单已入库数不出现负数；
     * 暂存中的不够撤回（已处置）时不能冲销（audit BUS-072）
     */
    public void withdrawAfterReversal(Collection<Long> soItemIds, PurchaseReceiptDO r, Long operatorId) {
        List<Long> ids = soItemIds.stream().filter(x -> x != null && x > 0).distinct().toList();
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, Integer> qualified = receivingQty.qualifiedBySoItem(ids);
        List<String> text = new ArrayList<>();
        for (Long soItemId : ids) {
            int lacking = -qualified.getOrDefault(soItemId, 0);
            if (lacking <= 0) {
                continue;
            }
            List<StockHoldDO> rows = holdMapper.selectList(new LambdaQueryWrapper<StockHoldDO>()
                    .eq(StockHoldDO::getSoItemId, soItemId)
                    .eq(StockHoldDO::getReceiptId, 0L)
                    .eq(StockHoldDO::getSourceType, WarehouseConstants.HOLD_SRC_PARTIAL_CANCEL)
                    .eq(StockHoldDO::getStatus, WarehouseConstants.HOLD_ACTIVE)
                    .isNull(StockHoldDO::getDeletedAt)
                    .orderByDesc(StockHoldDO::getId));
            String model = null;
            int withdrawn = 0;
            for (StockHoldDO h : rows) {
                if (lacking <= 0) {
                    break;
                }
                int take = Math.min(lacking, h.getQuantity());
                if (take == h.getQuantity()) {
                    h.setDeletedAt(LocalDateTime.now());
                } else {
                    h.setQuantity(h.getQuantity() - take);
                }
                holdMapper.updateById(h);
                lacking -= take;
                withdrawn += take;
                model = h.getModel();
            }
            if (lacking > 0) {
                throw new BizException("部分取消时转入的闲置库存已处置，不能冲销");
            }
            text.add(model + " × " + withdrawn);
        }
        if (!text.isEmpty()) {
            logs.add(r.getPoId(), "撤回闲置库存", r.getGrNo() + " 冲销，订单部分取消时转入的闲置库存一并撤回：" + String.join("、", text),
                    operatorId);
        }
    }
}
