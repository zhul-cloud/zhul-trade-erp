package com.zhul.erp.modules.warehouse.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.framework.tenant.TenantScope;
import com.zhul.erp.modules.purchase.entity.PurchaseOrderDO;
import com.zhul.erp.modules.purchase.repository.PurchaseOrderMapper;
import com.zhul.erp.modules.system.notification.constants.NotificationNode;
import com.zhul.erp.modules.system.notification.service.NotificationService;
import com.zhul.erp.modules.warehouse.constants.WarehouseConstants;
import com.zhul.erp.modules.warehouse.entity.SupplierShipmentDO;
import com.zhul.erp.modules.warehouse.entity.SupplierShipmentItemDO;
import com.zhul.erp.modules.warehouse.repository.SupplierShipmentItemMapper;
import com.zhul.erp.modules.warehouse.repository.SupplierShipmentMapper;
import com.zhul.erp.modules.warehouse.support.WarehouseNotices;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 每天 9:00：到了预计到货日还没收货的发货单（不含直发货代），提醒收货人（spec system/notification「收货通知」）。
 * 同一张发货单只提醒一次，之后仍没收货的留在待处理里。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ArrivalDueNotifyJob {

    private final SupplierShipmentMapper shipmentMapper;
    private final SupplierShipmentItemMapper itemMapper;
    private final PurchaseOrderMapper orderMapper;
    private final NotificationService notificationService;
    private final WarehouseNotices notices;

    @Scheduled(cron = "${zhul.notification.arrival-due-cron:0 0 9 * * *}", zone = "Asia/Shanghai")
    public void daily() {
        run(LocalDate.now());
    }

    public void run(LocalDate today) {
        List<Object> tenants = TenantScope.platform(() -> shipmentMapper.selectObjs(new QueryWrapper<SupplierShipmentDO>()
                .select("DISTINCT tenant_id")
                .eq("status", WarehouseConstants.SHIP_IN_TRANSIT)
                .le("expected_arrival_date", today)
                .isNull("direct_forwarder_id")
                .isNull("deleted_at")));
        for (Object t : tenants) {
            int tenantId = ((Number) t).intValue();
            try {
                TenantContext.runAs(tenantId, () -> notifyTenant(today));
            } catch (RuntimeException e) {
                log.error("到期未收货提醒失败，tenantId={}", tenantId, e);
            }
        }
    }

    /** 一个租户；调用方负责设置租户上下文 */
    void notifyTenant(LocalDate today) {
        List<SupplierShipmentDO> due = shipmentMapper.selectList(new LambdaQueryWrapper<SupplierShipmentDO>()
                .eq(SupplierShipmentDO::getStatus, WarehouseConstants.SHIP_IN_TRANSIT)
                .le(SupplierShipmentDO::getExpectedArrivalDate, today)
                .isNull(SupplierShipmentDO::getDirectForwarderId)
                .isNull(SupplierShipmentDO::getDeletedAt));
        if (due.isEmpty()) {
            return;
        }
        Set<String> sent = notificationService.existingKeys(NotificationNode.ARRIVAL_DUE,
                due.stream().map(s -> "sd-" + s.getId()).toList());
        due = due.stream().filter(s -> !sent.contains("sd-" + s.getId())).toList();
        if (due.isEmpty()) {
            return;
        }
        Map<Long, PurchaseOrderDO> pos = orderMapper.selectBatchIds(due.stream().map(SupplierShipmentDO::getPoId).distinct().toList())
                .stream().collect(Collectors.toMap(PurchaseOrderDO::getId, p -> p));
        Map<Long, List<SupplierShipmentItemDO>> items = itemMapper.selectList(new LambdaQueryWrapper<SupplierShipmentItemDO>()
                        .in(SupplierShipmentItemDO::getShipmentId, due.stream().map(SupplierShipmentDO::getId).toList())
                        .isNull(SupplierShipmentItemDO::getDeletedAt))
                .stream().collect(Collectors.groupingBy(SupplierShipmentItemDO::getShipmentId));
        for (SupplierShipmentDO s : due) {
            PurchaseOrderDO p = pos.get(s.getPoId());
            if (p != null) {
                notices.arrivalDue(s, p, items.getOrDefault(s.getId(), List.of()));
            }
        }
    }
}
