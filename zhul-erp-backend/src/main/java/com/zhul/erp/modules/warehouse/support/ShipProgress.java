package com.zhul.erp.modules.warehouse.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.modules.purchase.entity.PurchaseOrderItemDO;
import com.zhul.erp.modules.warehouse.constants.WarehouseConstants;
import com.zhul.erp.modules.warehouse.entity.SupplierShipmentDO;
import com.zhul.erp.modules.warehouse.repository.SupplierShipmentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 采购单的发货进度（不落库）：未发货 / 部分发货 / 已发货 / 已入库，口径同 {@link ReceivingQty}。
 * 列表展示在 Java 里算（{@link #of}），筛选用同样口径的 SQL 条件（{@link #condition}），两边须保持一致。
 */
@Component
@RequiredArgsConstructor
public class ShipProgress {

    public static final String UNSHIPPED = "UNSHIPPED";
    public static final String PARTIAL = "PARTIAL";
    public static final String SHIPPED = "SHIPPED";
    public static final String RECEIVED = "RECEIVED";
    /** 只用于筛选：还有未发且已过预计发货日期 */
    public static final String OVERDUE = "OVERDUE";
    public static final Map<String, String> NAMES = Map.of(UNSHIPPED, "未发货", PARTIAL, "部分发货", SHIPPED, "已发货", RECEIVED, "已入库",
            OVERDUE, "逾期未发");

    private static final String ITEMS = "FROM purchase_order_item pi WHERE pi.po_id = purchase_order.id AND pi.deleted_at IS NULL";
    /** 采购单行的已发数量，同 ReceivingQtyMapper.shippedByPoItem */
    private static final String SHIPPED_QTY = "(COALESCE((SELECT SUM(si.quantity) FROM supplier_shipment_item si "
            + "JOIN supplier_shipment s ON s.id = si.shipment_id WHERE si.po_item_id = pi.id AND si.deleted_at IS NULL "
            + "AND s.deleted_at IS NULL AND s.status = 1), 0) "
            + "+ COALESCE((SELECT SUM(LEAST(ri.received_qty, ri.shipped_qty)) FROM purchase_receipt_item ri "
            + "JOIN purchase_receipt r ON r.id = ri.receipt_id WHERE ri.po_item_id = pi.id AND ri.deleted_at IS NULL "
            + "AND r.deleted_at IS NULL AND r.status = 1), 0) "
            + "- COALESCE((SELECT SUM(d.quantity) FROM receiving_discrepancy d WHERE d.po_item_id = pi.id "
            + "AND d.deleted_at IS NULL AND d.status = 2 AND d.resolution IN (3, 4)), 0))";
    /** 采购单行的合格入库数量，同 ReceivingQtyMapper.qualifiedByPoItem */
    private static final String RECEIVED_QTY = "(COALESCE((SELECT SUM(LEAST(ri.qualified_qty, ri.shipped_qty)) FROM purchase_receipt_item ri "
            + "JOIN purchase_receipt r ON r.id = ri.receipt_id WHERE ri.po_item_id = pi.id AND ri.deleted_at IS NULL "
            + "AND r.deleted_at IS NULL AND r.status = 1), 0) "
            + "+ COALESCE((SELECT SUM(d.quantity) FROM receiving_discrepancy d WHERE d.po_item_id = pi.id "
            + "AND d.deleted_at IS NULL AND d.status = 2 AND d.resolution = 5), 0))";
    private static final String HAS_UNSHIPPED = "EXISTS (SELECT 1 " + ITEMS + " AND " + SHIPPED_QTY + " < pi.quantity)";
    private static final String ANY_SHIPPED = "EXISTS (SELECT 1 " + ITEMS + " AND " + SHIPPED_QTY + " > 0)";
    private static final String HAS_UNRECEIVED = "EXISTS (SELECT 1 " + ITEMS + " AND " + RECEIVED_QTY + " < pi.quantity)";

    private final ReceivingQty receivingQty;
    private final SupplierShipmentMapper shipmentMapper;

    /** shipped：已发件数（每行不超过订购数）；earliestArrival：在途发货单最早的预计到货日期 */
    public record Result(String code, String name, int shipped, int total, LocalDate earliestArrival) {
    }

    /** 采购单 ID → 它的行（只传已下单的采购单） */
    public Map<Long, Result> of(Map<Long, List<PurchaseOrderItemDO>> linesByPo) {
        List<Long> itemIds = linesByPo.values().stream().flatMap(List::stream).map(PurchaseOrderItemDO::getId).toList();
        Map<Long, Integer> shipped = receivingQty.shippedByPoItem(itemIds);
        Map<Long, Integer> received = receivingQty.qualifiedByPoItem(itemIds);
        Map<Long, LocalDate> arrivals = earliestArrival(linesByPo.keySet());
        Map<Long, Result> out = new HashMap<>(linesByPo.size() * 2 + 1);
        linesByPo.forEach((poId, lines) -> {
            boolean allReceived = true;
            boolean allShipped = true;
            boolean anyShipped = false;
            int sent = 0;
            int total = 0;
            for (PurchaseOrderItemDO i : lines) {
                int q = i.getQuantity();
                int s = shipped.getOrDefault(i.getId(), 0);
                allReceived &= received.getOrDefault(i.getId(), 0) >= q;
                allShipped &= s >= q;
                anyShipped |= s > 0;
                sent += Math.max(0, Math.min(s, q));
                total += q;
            }
            String code = allReceived ? RECEIVED : allShipped ? SHIPPED : anyShipped ? PARTIAL : UNSHIPPED;
            out.put(poId, new Result(code, NAMES.get(code), sent, total, arrivals.get(poId)));
        });
        return out;
    }

    /** 筛选条件（拼在 purchase_order 的查询上，只含固定 SQL）；未知值返回 null */
    public static String condition(String code) {
        String ordered = "purchase_order.status = 2 AND ";
        return switch (code == null ? "" : code) {
            case UNSHIPPED -> ordered + HAS_UNSHIPPED + " AND NOT " + ANY_SHIPPED;
            case PARTIAL -> ordered + ANY_SHIPPED + " AND " + HAS_UNSHIPPED;
            case SHIPPED -> ordered + "NOT " + HAS_UNSHIPPED + " AND " + HAS_UNRECEIVED;
            case RECEIVED -> ordered + "NOT " + HAS_UNRECEIVED;
            case OVERDUE -> ordered + "purchase_order.expected_ship_date < CURDATE() AND " + HAS_UNSHIPPED;
            default -> null;
        };
    }

    /** 待发货：已下单且还有未发数量 */
    public static String pendingCondition() {
        return "purchase_order.status = 2 AND " + HAS_UNSHIPPED;
    }

    private Map<Long, LocalDate> earliestArrival(Collection<Long> poIds) {
        Map<Long, LocalDate> out = new HashMap<>();
        if (poIds.isEmpty()) {
            return out;
        }
        shipmentMapper.selectList(new LambdaQueryWrapper<SupplierShipmentDO>()
                        .select(SupplierShipmentDO::getPoId, SupplierShipmentDO::getExpectedArrivalDate)
                        .in(SupplierShipmentDO::getPoId, poIds)
                        .eq(SupplierShipmentDO::getStatus, WarehouseConstants.SHIP_IN_TRANSIT)
                        .isNotNull(SupplierShipmentDO::getExpectedArrivalDate)
                        .isNull(SupplierShipmentDO::getDeletedAt))
                .stream().filter(s -> Objects.nonNull(s.getExpectedArrivalDate()))
                .sorted(Comparator.comparing(SupplierShipmentDO::getExpectedArrivalDate).reversed())
                .forEach(s -> out.put(s.getPoId(), s.getExpectedArrivalDate()));
        return out;
    }
}
