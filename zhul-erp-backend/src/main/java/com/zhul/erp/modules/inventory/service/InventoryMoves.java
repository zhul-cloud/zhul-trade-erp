package com.zhul.erp.modules.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.modules.inventory.constants.InvConstants;
import com.zhul.erp.modules.inventory.dto.InvMoveVO;
import com.zhul.erp.modules.inventory.entity.InvBatchDO;
import com.zhul.erp.modules.inventory.entity.InvMoveDO;
import com.zhul.erp.modules.inventory.entity.InvWarehouseDO;
import com.zhul.erp.modules.inventory.repository.InvBatchMapper;
import com.zhul.erp.modules.inventory.repository.InvMoveMapper;
import com.zhul.erp.modules.inventory.repository.InvWarehouseMapper;
import com.zhul.erp.modules.purchase.purchasereturn.entity.PurchaseReturnDO;
import com.zhul.erp.modules.purchase.purchasereturn.entity.PurchaseReturnItemDO;
import com.zhul.erp.modules.purchase.purchasereturn.repository.PurchaseReturnItemMapper;
import com.zhul.erp.modules.purchase.purchasereturn.repository.PurchaseReturnMapper;
import com.zhul.erp.modules.sales.entity.SalesOrderDO;
import com.zhul.erp.modules.sales.entity.SalesOrderItemDO;
import com.zhul.erp.modules.sales.repository.SalesOrderItemMapper;
import com.zhul.erp.modules.sales.repository.SalesOrderMapper;
import com.zhul.erp.modules.sales.support.PiStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** 库存流水的显示：仓库、商品、批次、预占对象单号（库存流水页与各单据详情共用） */
@Service
@RequiredArgsConstructor
public class InventoryMoves {

    private final InvMoveMapper moveMapper;
    private final InvWarehouseMapper warehouseMapper;
    private final InvBatchMapper batchMapper;
    private final InventoryProducts products;
    private final SalesOrderItemMapper soItemMapper;
    private final SalesOrderMapper soMapper;
    private final PurchaseReturnItemMapper prItemMapper;
    private final PurchaseReturnMapper prMapper;

    /** 某张单据的全部流水（含冲销），按时间先后 */
    public List<InvMoveVO> ofDoc(String docType, Long docId) {
        return toVos(moveMapper.selectList(new LambdaQueryWrapper<InvMoveDO>()
                .eq(InvMoveDO::getTenantId, PiStore.tenantId())
                .eq(InvMoveDO::getDocType, docType)
                .eq(InvMoveDO::getDocId, docId)
                .orderByAsc(InvMoveDO::getId)));
    }

    public List<InvMoveVO> toVos(List<InvMoveDO> moves) {
        if (moves.isEmpty()) {
            return List.of();
        }
        Map<Long, String> warehouses = ids(moves, InvMoveDO::getWarehouseId).isEmpty() ? Map.of()
                : warehouseMapper.selectBatchIds(ids(moves, InvMoveDO::getWarehouseId)).stream()
                .collect(Collectors.toMap(InvWarehouseDO::getId, InvWarehouseDO::getName));
        Map<Long, String> batches = batchMapper.selectBatchIds(ids(moves, InvMoveDO::getBatchId)).stream()
                .collect(Collectors.toMap(InvBatchDO::getId, InvBatchDO::getBatchNo));
        Map<Long, InventoryProducts.Label> labels = products.labels(ids(moves, InvMoveDO::getProductId));
        Map<String, String> targets = targetNos(moves);
        return moves.stream().map(m -> {
            InvMoveVO v = new InvMoveVO();
            v.setId(m.getId());
            v.setMoveTime(m.getMoveTime());
            v.setWarehouseId(m.getWarehouseId());
            v.setWarehouseName(warehouses.getOrDefault(m.getWarehouseId(), ""));
            v.setProductId(m.getProductId());
            InventoryProducts.Label l = labels.getOrDefault(m.getProductId(), InventoryProducts.Label.NONE);
            v.setModel(l.model());
            v.setBrand(l.brand());
            v.setCategory(l.category());
            v.setBatchId(m.getBatchId());
            v.setBatchNo(batches.getOrDefault(m.getBatchId(), ""));
            v.setStockStatus(m.getStockStatus());
            v.setStockStatusName(InvConstants.STATUS_NAMES.get(m.getStockStatus()));
            v.setOpType(m.getOpType());
            v.setOpName(InvConstants.OP_NAMES.get(m.getOpType()));
            v.setQuantity(m.getQuantity());
            v.setUnitPriceCny(m.getUnitPriceCny());
            v.setAmountCny(m.getAmountCny());
            v.setBizType(m.getBizType());
            v.setBizName(InvConstants.BIZ_NAMES.getOrDefault(m.getBizType(), m.getBizType()));
            v.setBizLabel(m.getBizLabel());
            v.setDocType(m.getDocType());
            v.setDocId(m.getDocId());
            v.setDocNo(m.getDocNo());
            v.setTargetType(m.getTargetType());
            v.setTargetId(m.getTargetId());
            v.setTargetNo(m.getTargetType() == null ? "" : targets.getOrDefault(m.getTargetType() + ":" + m.getTargetId(), ""));
            v.setReversal(m.getReverseOf() != null);
            v.setOperatorName(Objects.toString(m.getOperatorName(), ""));
            return v;
        }).toList();
    }

    /** 预占对象的单号：「类型:ID」→ 销售订单号或采购退货单号 */
    public Map<String, String> targetNos(Collection<InvMoveDO> moves) {
        Map<Integer, Set<Long>> byType = moves.stream().filter(m -> m.getTargetType() != null)
                .collect(Collectors.groupingBy(InvMoveDO::getTargetType, Collectors.mapping(InvMoveDO::getTargetId, Collectors.toSet())));
        return targetNos(byType);
    }

    public Map<String, String> targetNos(Map<Integer, Set<Long>> byType) {
        Map<String, String> out = new HashMap<>();
        Set<Long> soItems = byType.getOrDefault(InvConstants.TARGET_SO_ITEM, Set.of());
        if (!soItems.isEmpty()) {
            List<SalesOrderItemDO> items = soItemMapper.selectBatchIds(soItems);
            Map<Long, String> sos = items.isEmpty() ? Map.of() : soMapper.selectBatchIds(items.stream().map(SalesOrderItemDO::getSoId)
                    .distinct().toList()).stream().collect(Collectors.toMap(SalesOrderDO::getId, SalesOrderDO::getSoNo));
            items.forEach(i -> out.put(InvConstants.TARGET_SO_ITEM + ":" + i.getId(), sos.getOrDefault(i.getSoId(), "")));
        }
        Set<Long> prItems = byType.getOrDefault(InvConstants.TARGET_RETURN_ITEM, Set.of());
        if (!prItems.isEmpty()) {
            List<PurchaseReturnItemDO> items = prItemMapper.selectBatchIds(prItems);
            Map<Long, String> prs = items.isEmpty() ? Map.of() : prMapper.selectBatchIds(items.stream()
                            .map(PurchaseReturnItemDO::getPurchaseReturnId).distinct().toList()).stream()
                    .collect(Collectors.toMap(PurchaseReturnDO::getId, PurchaseReturnDO::getReturnNo));
            items.forEach(i -> out.put(InvConstants.TARGET_RETURN_ITEM + ":" + i.getId(), prs.getOrDefault(i.getPurchaseReturnId(), "")));
        }
        return out;
    }

    private static List<Long> ids(List<InvMoveDO> moves, java.util.function.Function<InvMoveDO, Long> f) {
        return moves.stream().map(f).filter(Objects::nonNull).distinct().toList();
    }
}
