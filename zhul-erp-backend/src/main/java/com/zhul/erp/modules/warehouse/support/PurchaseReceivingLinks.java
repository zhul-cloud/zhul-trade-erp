package com.zhul.erp.modules.warehouse.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.modules.purchase.dto.PurchaseOrderVO;
import com.zhul.erp.modules.warehouse.constants.WarehouseConstants;
import com.zhul.erp.modules.warehouse.entity.PurchaseReceiptDO;
import com.zhul.erp.modules.warehouse.entity.PurchaseReceiptItemDO;
import com.zhul.erp.modules.warehouse.entity.SupplierShipmentDO;
import com.zhul.erp.modules.warehouse.entity.SupplierShipmentItemDO;
import com.zhul.erp.modules.warehouse.repository.PurchaseReceiptItemMapper;
import com.zhul.erp.modules.warehouse.repository.PurchaseReceiptMapper;
import com.zhul.erp.modules.warehouse.repository.SupplierShipmentItemMapper;
import com.zhul.erp.modules.warehouse.repository.SupplierShipmentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 采购单读取发货与入库：详情里的发货单、入库单，修改与取消时的限制（只读） */
@Component
@RequiredArgsConstructor
public class PurchaseReceivingLinks {

    private final SupplierShipmentMapper shipmentMapper;
    private final SupplierShipmentItemMapper shipmentItemMapper;
    private final PurchaseReceiptMapper receiptMapper;
    private final PurchaseReceiptItemMapper receiptItemMapper;

    /** 有在途或已入库的发货单 */
    public boolean hasShipments(Long poId) {
        return shipmentMapper.selectCount(new LambdaQueryWrapper<SupplierShipmentDO>()
                .eq(SupplierShipmentDO::getPoId, poId)
                .in(SupplierShipmentDO::getStatus, WarehouseConstants.SHIP_IN_TRANSIT, WarehouseConstants.SHIP_RECEIVED)
                .isNull(SupplierShipmentDO::getDeletedAt)) > 0;
    }

    /** 采购单的发货单（含作废的），新的在前 */
    public List<PurchaseOrderVO.ShipmentRef> shipments(Long poId) {
        List<SupplierShipmentDO> rows = shipmentMapper.selectList(new LambdaQueryWrapper<SupplierShipmentDO>()
                .eq(SupplierShipmentDO::getPoId, poId)
                .isNull(SupplierShipmentDO::getDeletedAt)
                .orderByDesc(SupplierShipmentDO::getId));
        Map<Long, Integer> qty = new HashMap<>();
        if (!rows.isEmpty()) {
            shipmentItemMapper.selectList(new LambdaQueryWrapper<SupplierShipmentItemDO>()
                            .in(SupplierShipmentItemDO::getShipmentId, rows.stream().map(SupplierShipmentDO::getId).toList())
                            .isNull(SupplierShipmentItemDO::getDeletedAt))
                    .forEach(i -> qty.merge(i.getShipmentId(), i.getQuantity(), Integer::sum));
        }
        return rows.stream().map(s -> {
            PurchaseOrderVO.ShipmentRef x = new PurchaseOrderVO.ShipmentRef();
            x.setId(s.getId());
            x.setSdNo(s.getSdNo());
            x.setShipDate(s.getShipDate());
            x.setCarrier(s.getCarrier());
            x.setTrackingNo(s.getTrackingNo());
            x.setTotalQuantity(qty.getOrDefault(s.getId(), 0));
            x.setSource(s.getSource());
            x.setSourceName(WarehouseConstants.SHIP_SOURCE_NAMES.get(s.getSource()));
            x.setStatus(s.getStatus());
            x.setStatusName(WarehouseConstants.SHIP_STATUS_NAMES.get(s.getStatus()));
            return x;
        }).toList();
    }

    /** 采购单的入库单（含冲销的），新的在前 */
    public List<PurchaseOrderVO.ReceiptRef> receipts(Long poId) {
        List<PurchaseReceiptDO> rows = receiptMapper.selectList(new LambdaQueryWrapper<PurchaseReceiptDO>()
                .eq(PurchaseReceiptDO::getPoId, poId)
                .isNull(PurchaseReceiptDO::getDeletedAt)
                .orderByDesc(PurchaseReceiptDO::getId));
        Map<Long, List<PurchaseReceiptItemDO>> items = rows.isEmpty() ? Map.of()
                : receiptItemMapper.selectList(new LambdaQueryWrapper<PurchaseReceiptItemDO>()
                        .in(PurchaseReceiptItemDO::getReceiptId, rows.stream().map(PurchaseReceiptDO::getId).toList())
                        .isNull(PurchaseReceiptItemDO::getDeletedAt))
                .stream().collect(Collectors.groupingBy(PurchaseReceiptItemDO::getReceiptId));
        Map<Long, String> sdNos = new HashMap<>();
        List<Long> shipmentIds = rows.stream().map(PurchaseReceiptDO::getShipmentId).distinct().toList();
        if (!shipmentIds.isEmpty()) {
            shipmentMapper.selectBatchIds(shipmentIds).forEach(s -> sdNos.put(s.getId(), s.getSdNo()));
        }
        return rows.stream().map(r -> {
            List<PurchaseReceiptItemDO> lines = items.getOrDefault(r.getId(), List.of());
            PurchaseOrderVO.ReceiptRef x = new PurchaseOrderVO.ReceiptRef();
            x.setId(r.getId());
            x.setGrNo(r.getGrNo());
            x.setReceivedDate(r.getReceivedDate());
            x.setSdNo(sdNos.get(r.getShipmentId()));
            x.setQualifiedQty(lines.stream().mapToInt(PurchaseReceiptItemDO::getQualifiedQty).sum());
            x.setDefectiveQty(lines.stream().mapToInt(PurchaseReceiptItemDO::getDefectiveQty).sum());
            x.setStatus(r.getStatus());
            x.setStatusName(WarehouseConstants.GR_STATUS_NAMES.get(r.getStatus()));
            return x;
        }).toList();
    }
}
