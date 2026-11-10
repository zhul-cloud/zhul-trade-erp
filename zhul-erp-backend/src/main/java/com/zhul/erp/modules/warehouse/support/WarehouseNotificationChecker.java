package com.zhul.erp.modules.warehouse.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.modules.system.notification.constants.NotificationNode;
import com.zhul.erp.modules.system.notification.support.NotificationTodoChecker;
import com.zhul.erp.modules.warehouse.constants.WarehouseConstants;
import com.zhul.erp.modules.warehouse.entity.ReceivingDiscrepancyDO;
import com.zhul.erp.modules.warehouse.entity.SupplierShipmentDO;
import com.zhul.erp.modules.warehouse.repository.ReceivingDiscrepancyMapper;
import com.zhul.erp.modules.warehouse.repository.SupplierShipmentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/** 收货待办：发货单已收货或作废；到货差异已处理 */
@Component
@RequiredArgsConstructor
public class WarehouseNotificationChecker implements NotificationTodoChecker {

    private final SupplierShipmentMapper shipmentMapper;
    private final ReceivingDiscrepancyMapper discrepancyMapper;

    @Override
    public Set<NotificationNode> nodes() {
        return Set.of(NotificationNode.SUPPLIER_SHIPPED, NotificationNode.ARRIVAL_DUE, NotificationNode.DISCREPANCY);
    }

    @Override
    public Set<Long> open(NotificationNode node, Long recipientId, Collection<Long> bizIds) {
        if (node == NotificationNode.DISCREPANCY) {
            return discrepancyMapper.selectList(new LambdaQueryWrapper<ReceivingDiscrepancyDO>()
                            .select(ReceivingDiscrepancyDO::getId)
                            .in(ReceivingDiscrepancyDO::getId, bizIds)
                            .eq(ReceivingDiscrepancyDO::getStatus, WarehouseConstants.DIFF_PENDING)
                            .isNull(ReceivingDiscrepancyDO::getDeletedAt))
                    .stream().map(ReceivingDiscrepancyDO::getId).collect(Collectors.toSet());
        }
        return shipmentMapper.selectList(new LambdaQueryWrapper<SupplierShipmentDO>()
                        .select(SupplierShipmentDO::getId)
                        .in(SupplierShipmentDO::getId, bizIds)
                        .eq(SupplierShipmentDO::getStatus, WarehouseConstants.SHIP_IN_TRANSIT)
                        .isNull(SupplierShipmentDO::getDeletedAt))
                .stream().map(SupplierShipmentDO::getId).collect(Collectors.toSet());
    }
}
