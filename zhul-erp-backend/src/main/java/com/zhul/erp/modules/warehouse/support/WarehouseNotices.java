package com.zhul.erp.modules.warehouse.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.modules.purchase.entity.PurchaseOrderDO;
import com.zhul.erp.modules.purchase.support.PurchaseLinks;
import com.zhul.erp.modules.sales.constants.SalesConstants;
import com.zhul.erp.modules.sales.entity.SalesOrderDO;
import com.zhul.erp.modules.sales.entity.SalesOrderItemDO;
import com.zhul.erp.modules.sales.repository.SalesOrderItemMapper;
import com.zhul.erp.modules.sales.repository.SalesOrderMapper;
import com.zhul.erp.modules.system.notification.constants.NotificationNode;
import com.zhul.erp.modules.system.notification.support.Notice;
import com.zhul.erp.modules.system.notification.support.Notifier;
import com.zhul.erp.modules.warehouse.constants.WarehouseConstants;
import com.zhul.erp.modules.warehouse.entity.PurchaseReceiptDO;
import com.zhul.erp.modules.warehouse.entity.PurchaseReceiptItemDO;
import com.zhul.erp.modules.warehouse.entity.ReceivingDiscrepancyDO;
import com.zhul.erp.modules.warehouse.entity.SupplierShipmentDO;
import com.zhul.erp.modules.warehouse.entity.SupplierShipmentItemDO;
import com.zhul.erp.modules.warehouse.repository.ReceivingDiscrepancyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

/** 发货与收货相关的通知（spec system/notification「收货通知」「订单货已到齐通知」） */
@Component
@RequiredArgsConstructor
public class WarehouseNotices {

    private static final String RECEIPTS = "/warehouse/receipts";
    private static final String ORDER = "/sales/orders/";
    private static final DateTimeFormatter MD = DateTimeFormatter.ofPattern("MM-dd");
    private static final int MAX_MODELS = 5;

    private final Notifier notifier;
    private final SalesOrderMapper orderMapper;
    private final SalesOrderItemMapper orderItemMapper;
    private final ReceivingDiscrepancyMapper discrepancyMapper;
    private final PurchaseLinks purchaseLinks;

    /** 登记供应商发货：收货人收待办（直发货代的不发）；来源订单的业务员收知会 */
    public void shipped(SupplierShipmentDO s, PurchaseOrderDO p, List<SupplierShipmentItemDO> items) {
        boolean direct = s.getDirectForwarderId() != null;
        if (!direct) {
            notifier.send(shipmentTodo(NotificationNode.SUPPLIER_SHIPPED, s, p, items));
        }
        String tail = direct ? "直发货代，请在出运单确认实收" : arrival(s.getExpectedArrivalDate());
        bySalesOrder(items, SupplierShipmentItemDO::getSoItemId, SupplierShipmentItemDO::getModel, SupplierShipmentItemDO::getQuantity)
                .forEach((o, text) -> notifier.send(Notice.of(NotificationNode.SHIPPED_FOR_SALES, o.getSoNo(), ORDER + o.getId())
                        .content(text + " · " + tail)
                        .to(o.getOwnerId())));
    }

    /** 到了预计到货日还没收货（定时任务调用）：发给收货人，采购员本人登记的发货也发 */
    public void arrivalDue(SupplierShipmentDO s, PurchaseOrderDO p, List<SupplierShipmentItemDO> items) {
        notifier.send(shipmentTodo(NotificationNode.ARRIVAL_DUE, s, p, items));
    }

    private Notice shipmentTodo(NotificationNode node, SupplierShipmentDO s, PurchaseOrderDO p, List<SupplierShipmentItemDO> items) {
        int pieces = items.stream().mapToInt(SupplierShipmentItemDO::getQuantity).sum();
        String carrier = s.getCarrier() == null || s.getCarrier().isBlank() ? ""
                : s.getCarrier() + (s.getTrackingNo() == null || s.getTrackingNo().isBlank() ? "" : " " + s.getTrackingNo()) + " · ";
        return Notice.of(node, s.getSdNo(), RECEIPTS)
                .merge("sd-" + s.getId())
                .ids(List.of(s.getId()))
                .content("采购订单 " + p.getPoNo() + " · " + carrier + items.size() + " 个型号 " + pieces + " 件 · "
                        + arrival(s.getExpectedArrivalDate()))
                .to(p.getPurchaserId());
    }

    /** 收货完成：来源订单的业务员收知会；有到货差异的通知采购订单的采购员；订单货到齐时再通知业务员 */
    public void received(PurchaseReceiptDO r, PurchaseOrderDO p, List<PurchaseReceiptItemDO> items) {
        Map<SalesOrderDO, String> bySo = bySalesOrder(items, PurchaseReceiptItemDO::getSoItemId, PurchaseReceiptItemDO::getModel,
                PurchaseReceiptItemDO::getQualifiedQty);
        bySo.forEach((o, text) -> notifier.send(Notice.of(NotificationNode.GOODS_RECEIVED, o.getSoNo(), ORDER + o.getId())
                .merge(o.getId())
                .content(text + " 合格入库")
                .to(o.getOwnerId())));
        List<ReceivingDiscrepancyDO> diffs = discrepancyMapper.selectList(new LambdaQueryWrapper<ReceivingDiscrepancyDO>()
                .eq(ReceivingDiscrepancyDO::getReceiptId, r.getId())
                .eq(ReceivingDiscrepancyDO::getStatus, WarehouseConstants.DIFF_PENDING)
                .isNull(ReceivingDiscrepancyDO::getDeletedAt));
        if (!diffs.isEmpty()) {
            notifier.send(Notice.of(NotificationNode.DISCREPANCY, p.getPoNo(), "/purchase/orders/" + p.getId())
                    .merge(p.getId())
                    .ids(diffs.stream().map(ReceivingDiscrepancyDO::getId).toList())
                    .content(diffs.stream().map(d -> d.getModel() + " " + WarehouseConstants.DIFF_TYPE_NAMES.get(d.getType())
                            + " " + d.getQuantity() + " 个").limit(MAX_MODELS).collect(Collectors.joining(" · ")))
                    .to(p.getPurchaserId()));
        }
        bySo.keySet().forEach(this::readyIfComplete);
    }

    /** 订单全部有采购的型号都已入库（待采购、待发货、在途都为 0）时通知业务员，每张订单只发一次 */
    private void readyIfComplete(SalesOrderDO o) {
        if (o.getStatus() != SalesConstants.SO_ACTIVE) {
            return;
        }
        List<SalesOrderItemDO> items = orderItemMapper.selectList(new LambdaQueryWrapper<SalesOrderItemDO>()
                .eq(SalesOrderItemDO::getSoId, o.getId())
                .gt(SalesOrderItemDO::getQuantity, 0)
                .isNull(SalesOrderItemDO::getDeletedAt));
        Map<Long, PurchaseLinks.ItemPurchase> links = purchaseLinks.forSoItems(items.stream().map(SalesOrderItemDO::getId).toList());
        int tracked = 0;
        int pieces = 0;
        for (SalesOrderItemDO i : items) {
            PurchaseLinks.ItemPurchase lp = links.get(i.getId());
            if (lp == null || !lp.tracked()) {
                continue;
            }
            PurchaseLinks.Goods g = lp.goods(i.getQuantity());
            if (g.pendingPurchase() + g.pendingShip() + g.inTransit() > 0) {
                return;
            }
            tracked++;
            pieces += i.getQuantity();
        }
        if (tracked == 0) {
            return;
        }
        notifier.send(Notice.of(NotificationNode.ORDER_READY, o.getSoNo(), ORDER + o.getId())
                .merge(o.getId())
                .count(tracked)
                .content(tracked + " 个型号 " + pieces + " 件全部入库")
                .to(o.getOwnerId()));
    }

    /** 按来源销售订单汇总型号与数量（如「A × 40 · C × 10」） */
    private <T> Map<SalesOrderDO, String> bySalesOrder(List<T> items, Function<T, Long> soItem, Function<T, String> model,
                                                      ToIntFunction<T> qty) {
        List<Long> soItemIds = items.stream().map(soItem).filter(Objects::nonNull).distinct().toList();
        if (soItemIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> soOf = orderItemMapper.selectBatchIds(soItemIds).stream()
                .collect(Collectors.toMap(SalesOrderItemDO::getId, SalesOrderItemDO::getSoId));
        Map<Long, SalesOrderDO> orders = orderMapper.selectBatchIds(soOf.values().stream().distinct().toList()).stream()
                .collect(Collectors.toMap(SalesOrderDO::getId, x -> x));
        Map<Long, List<T>> grouped = new LinkedHashMap<>();
        for (T i : items) {
            Long so = soOf.get(soItem.apply(i));
            if (so != null && orders.containsKey(so) && qty.applyAsInt(i) > 0) {
                grouped.computeIfAbsent(so, k -> new java.util.ArrayList<>()).add(i);
            }
        }
        Map<SalesOrderDO, String> out = new LinkedHashMap<>();
        grouped.forEach((so, rows) -> {
            String head = rows.stream().limit(MAX_MODELS).map(i -> model.apply(i) + " × " + qty.applyAsInt(i))
                    .collect(Collectors.joining(" · "));
            out.put(orders.get(so), rows.size() > MAX_MODELS ? head + " 等 " + rows.size() + " 个型号" : head);
        });
        return out;
    }

    private static String arrival(LocalDate d) {
        return d == null ? "预计到货日未填" : "预计 " + d.format(MD) + " 到";
    }
}
