package com.zhul.erp.modules.inventory.service;

import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.framework.tenant.TenantScope;
import com.zhul.erp.modules.inventory.constants.InvConstants;
import com.zhul.erp.modules.inventory.entity.InvBatchDO;
import com.zhul.erp.modules.inventory.service.InventoryPosting.Doc;
import com.zhul.erp.modules.inventory.service.InventoryPosting.Line;
import com.zhul.erp.modules.inventory.service.InventoryPosting.Loc;
import com.zhul.erp.modules.inventory.service.InventoryPosting.Target;
import com.zhul.erp.modules.inventory.service.InventoryWarehouses.Price;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 库存期初（openspec inventory-foundation design 第 8 点，spec warehouse/inventory「期初」）：启动时按租户执行一次，
 * 每个租户一个事务，完成后在 sys_config 记 inventory.opening.done=1；失败的租户记日志，下次启动重试。
 * <p>
 * 按现有单据推算在库：采购入库单行一个批次（合格含折价接收，不良扣折价与已寄出的退货）；已交货代的按型号行从最早的批次扣，
 * 未出运的进货代仓；已处置的闲置库存扣掉；退货入库单按去向推算（未处理、转闲置未处置、退供应商待寄出的仍在库）。
 * 预占：仍属于订单、未出运的部分先占货代仓、再占本型号行的批次；待寄出的采购退货单行占对应批次。
 * 上线后新单据已由过账钩子入账：已有流水的采购入库单、退货入库单不再推算，已有交货代流水的出库单只扣不调入，避免重复。
 * 推算为负数的不入库，记 WARN 日志。期初流水日期为执行当天，业务类型「期初」。
 * 后台入口：租户列表在平台范围查询（JdbcTemplate 手写，跨租户），每个租户用 {@link TenantContext#runAs} 执行，SQL 都带本租户条件。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryOpening implements ApplicationRunner {

    private static final int PR_PENDING = 1;
    private static final int RES_DISCOUNT = 5;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final InventoryWarehouses warehouses;
    private final InventoryPosting posting;
    private final InventoryHooks hooks;
    private final InventoryReservations reservations;

    /** 某租户期初的结果 */
    public record Report(int tenantId, int lines, int quantity, List<String> negatives) {
    }

    @Override
    public void run(ApplicationArguments args) {
        List<Integer> tenants = TenantScope.platform(() -> jdbc.queryForList("SELECT id FROM tenant"
                + " UNION SELECT DISTINCT tenant_id FROM purchase_receipt UNION SELECT DISTINCT tenant_id FROM return_receipt"
                + " UNION SELECT DISTINCT tenant_id FROM outbound_order", Integer.class));
        Set<Integer> done = new HashSet<>(TenantScope.platform(() -> jdbc.queryForList("SELECT tenant_id FROM sys_config"
                + " WHERE config_key = ? AND config_value = '1' AND deleted_at IS NULL", Integer.class, InvConstants.CONFIG_OPENING_DONE)));
        for (Integer t : tenants) {
            if (t == null || done.contains(t)) {
                continue;
            }
            try {
                Report r = openTenant(t);
                log.info("库存期初完成，tenantId={}, 期初行数={}, 件数={}, 负数行={}", t, r.lines(), r.quantity(), r.negatives().size());
            } catch (RuntimeException e) {
                log.warn("库存期初失败，下次启动重试，tenantId={}", t, e);
            }
        }
    }

    /** 执行某租户的期初（已完成的直接返回空结果） */
    public Report openTenant(int tenantId) {
        Report[] out = new Report[1];
        TenantContext.runAs(tenantId, () -> tx.executeWithoutResult(s -> out[0] = open(tenantId)));
        return out[0];
    }

    // ---------------------------------------------------------------- 推算

    /** 一个批次在一个仓库的在库：合格、不良，以及预占 */
    private static final class Slot {
        final Long batchId;
        final long productId;
        final Long soItemId;
        Long receiptId;
        long warehouseId;
        int good;
        int bad;
        final Map<Target, Integer> goodRes = new LinkedHashMap<>();
        final Map<Target, Integer> badRes = new LinkedHashMap<>();

        Slot(Long batchId, long productId, Long soItemId, long warehouseId) {
            this.batchId = batchId;
            this.productId = productId;
            this.soItemId = soItemId;
            this.warehouseId = warehouseId;
        }

        int reservedGood() {
            return goodRes.values().stream().mapToInt(Integer::intValue).sum();
        }
    }

    private Report open(int tenant) {
        if ("1".equals(warehouses.config(InvConstants.CONFIG_OPENING_DONE))) {
            return new Report(tenant, 0, 0, List.of());
        }
        Long existing = jdbc.queryForObject("SELECT COUNT(*) FROM inv_move WHERE tenant_id = ? AND biz_type <> ?", Long.class,
                tenant, InvConstants.BIZ_OPENING);
        if (existing != null && existing > 0) {
            log.warn("库存期初前已有业务流水 {} 笔（上线后新单据），这些单据不再推算，tenantId={}", existing, tenant);
        }
        List<String> negatives = new ArrayList<>();
        long own = warehouses.own().getId();
        jdbc.queryForList("SELECT id, name, status FROM supplier WHERE tenant_id = ? AND supplier_type = 3 AND deleted_at IS NULL", tenant)
                .forEach(r -> warehouses.syncForwarder(((Number) r.get("id")).longValue(), (String) r.get("name"),
                        ((Number) r.get("status")).intValue()));

        List<Slot> slots = new ArrayList<>();
        purchaseSlots(tenant, own, slots, negatives);
        handedOver(tenant, own, slots, negatives);
        returnSlots(tenant, own, slots, negatives);
        reserveForOrders(tenant, own, slots);

        Doc doc = Doc.of(InvConstants.BIZ_OPENING, "OPENING", 0L, "期初").label("期初");
        List<Line> lines = new ArrayList<>();
        int qty = 0;
        for (Slot s : slots) {
            qty += addLines(lines, s, InvConstants.GOOD, s.good, s.goodRes, doc);
            qty += addLines(lines, s, InvConstants.DEFECTIVE, s.bad, s.badRes, doc);
        }
        posting.post(lines);
        warehouses.saveConfig(InvConstants.CONFIG_OPENING_DONE, "库存期初已完成", "1");
        negatives.forEach(n -> log.warn("库存期初推算为负数，未入库，tenantId={}：{}", tenant, n));
        return new Report(tenant, lines.size(), qty, negatives);
    }

    private static int addLines(List<Line> lines, Slot s, int status, int qty, Map<Target, Integer> res, Doc doc) {
        if (qty <= 0 || s.batchId == null) {
            return 0;
        }
        Loc loc = new Loc(s.warehouseId, s.productId, s.batchId, status);
        int left = qty;
        for (Map.Entry<Target, Integer> e : res.entrySet()) {
            int n = Math.min(left, e.getValue());
            if (n > 0) {
                lines.add(Line.of(loc, InvConstants.OP_IN, n, e.getKey(), doc));
                left -= n;
            }
        }
        if (left > 0) {
            lines.add(Line.of(loc, InvConstants.OP_IN, left, null, doc));
        }
        return qty;
    }

    /** 采购入库单行 → 批次（没有流水的有效入库单） */
    private void purchaseSlots(int tenant, long own, List<Slot> slots, List<String> negatives) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT i.id, i.receipt_id, r.gr_no, r.received_date, i.product_id, i.so_item_id,"
                + " i.po_item_id, i.model, COALESCE(i.qualified_qty, 0) good, COALESCE(i.defective_qty, 0) bad, s.direct_forwarder_id,"
                + " p.unit_price, p.net_price_cny, po.currency_code, po.exchange_rate"
                + " FROM purchase_receipt_item i JOIN purchase_receipt r ON r.id = i.receipt_id AND r.tenant_id = i.tenant_id"
                + " LEFT JOIN supplier_shipment s ON s.id = r.shipment_id AND s.tenant_id = r.tenant_id"
                + " LEFT JOIN purchase_order_item p ON p.id = i.po_item_id AND p.tenant_id = i.tenant_id"
                + " LEFT JOIN purchase_order po ON po.id = p.po_id AND po.tenant_id = p.tenant_id"
                + " WHERE i.tenant_id = ? AND r.status = 1 AND r.deleted_at IS NULL AND i.deleted_at IS NULL"
                + " AND NOT EXISTS (SELECT 1 FROM inv_move m WHERE m.tenant_id = r.tenant_id AND m.doc_type = 'GR' AND m.doc_id = r.id)"
                + " ORDER BY r.received_date, r.id, i.id", tenant);
        // 批次号的行序号按入库单全部行（与过账钩子一致）
        Map<Long, Integer> seq = new HashMap<>();
        jdbc.query("SELECT id, receipt_id FROM purchase_receipt_item WHERE tenant_id = ? ORDER BY receipt_id, id", rs -> {
            seq.put(rs.getLong(1), seq.size());
        }, tenant);
        Map<Long, Long> firstOfReceipt = new HashMap<>();
        jdbc.query("SELECT receipt_id, MIN(id) FROM purchase_receipt_item WHERE tenant_id = ? GROUP BY receipt_id", rs -> {
            firstOfReceipt.put(rs.getLong(1), rs.getLong(2));
        }, tenant);
        Map<Long, String> grades = hooks.grades(rows.stream().map(r -> longOf(r.get("so_item_id"))).toList());
        Map<Long, Slot> byItem = new HashMap<>();
        Map<Long, int[]> discount = sumBy("SELECT receipt_item_id, SUM(quantity) FROM receiving_discrepancy WHERE tenant_id = ?"
                + " AND resolution = " + RES_DISCOUNT + " AND status = 2 AND deleted_at IS NULL GROUP BY receipt_item_id", tenant);
        for (Map<String, Object> r : rows) {
            Long productId = longOf(r.get("product_id"));
            if (productId == null) {
                continue;
            }
            long itemId = ((Number) r.get("id")).longValue();
            long receiptId = ((Number) r.get("receipt_id")).longValue();
            int good = ((Number) r.get("good")).intValue();
            int bad = ((Number) r.get("bad")).intValue();
            int disc = discount.getOrDefault(itemId, new int[1])[0];
            good += disc;
            bad -= disc;
            Long direct = longOf(r.get("direct_forwarder_id"));
            long wh = direct == null ? own : warehouses.forwarder(direct).getId();
            Long soItemId = longOf(r.get("so_item_id"));
            int n = (int) (seq.get(itemId) - seq.get(firstOfReceipt.get(receiptId))) + 1;
            Price price = new Price(Objects.toString(r.get("currency_code"), "CNY"), (BigDecimal) r.get("unit_price"),
                    (BigDecimal) r.get("exchange_rate"), (BigDecimal) r.get("net_price_cny"));
            InvBatchDO b = warehouses.batchOf(InvConstants.BATCH_PURCHASE, itemId);
            if (b == null) {
                b = warehouses.newBatch(r.get("gr_no") + "-" + n, productId, grades.getOrDefault(soItemId, ""), InvConstants.BATCH_PURCHASE,
                        receiptId, itemId, longOf(r.get("po_item_id")), soItemId, toDate(r.get("received_date")), price);
            }
            Slot s = new Slot(b.getId(), productId, soItemId, wh);
            s.receiptId = receiptId;
            s.good = good;
            s.bad = bad;
            slots.add(s);
            byItem.put(itemId, s);
        }
        // 到货差异生成的采购退货：已寄出、已完成的扣掉；待寄出的仍在库并预占给退货单行
        for (Map<String, Object> r : jdbc.queryForList("SELECT pi.id, pi.quantity, pr.status, d.receipt_item_id, d.type"
                + " FROM purchase_return_item pi JOIN purchase_return pr ON pr.id = pi.purchase_return_id AND pr.tenant_id = pi.tenant_id"
                + " JOIN receiving_discrepancy d ON d.id = pi.discrepancy_id AND d.tenant_id = pi.tenant_id"
                + " WHERE pi.tenant_id = ? AND pi.deleted_at IS NULL AND pr.deleted_at IS NULL AND pr.status <> 4", tenant)) {
            Slot s = byItem.get(longOf(r.get("receipt_item_id")));
            if (s == null) {
                continue;
            }
            boolean defective = ((Number) r.get("type")).intValue() == 2;
            int q = ((Number) r.get("quantity")).intValue();
            if (((Number) r.get("status")).intValue() == PR_PENDING) {
                (defective ? s.badRes : s.goodRes).merge(Target.returnItem(longOf(r.get("id"))), q, Integer::sum);
            } else if (defective) {
                s.bad -= q;
            } else {
                s.good -= q;
            }
        }
        // 已处置（退回、报废、转样品）的到货差异暂存与部分取消闲置库存：货已不在
        for (Map<String, Object> r : jdbc.queryForList("SELECT h.source_type, h.quantity, h.receipt_id, h.so_item_id, d.receipt_item_id, h.model"
                + " FROM stock_hold h LEFT JOIN receiving_discrepancy d ON d.id = h.discrepancy_id AND d.tenant_id = h.tenant_id"
                + " WHERE h.tenant_id = ? AND h.deleted_at IS NULL AND h.status <> 1 AND h.source_type IN (1, 2)", tenant)) {
            // 部分取消的闲置库存没有差异行，按入库单与型号行找批次
            Long receiptId = longOf(r.get("receipt_id"));
            Long soItemId = longOf(r.get("so_item_id"));
            Slot s = byItem.get(longOf(r.get("receipt_item_id")));
            if (s == null) {
                s = slots.stream().filter(x -> Objects.equals(x.soItemId, soItemId) && Objects.equals(x.receiptId, receiptId))
                        .findFirst().orElse(null);
            }
            if (s != null) {
                s.good -= ((Number) r.get("quantity")).intValue();
            }
        }
        for (Slot s : slots) {
            if (s.good < 0 || s.bad < 0) {
                negatives.add("批次 " + s.batchId + " 合格 " + s.good + "、不良 " + s.bad);
                s.good = Math.max(0, s.good);
                s.bad = Math.max(0, s.bad);
            }
        }
    }

    /** 已交货代：从型号行的批次扣（先扣直发到该货代的，再从公司仓库最早的批次扣）；未出运的进货代仓 */
    private void handedOver(int tenant, long own, List<Slot> slots, List<String> negatives) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT o.id, o.ob_no, o.forwarder_id, oi.so_item_id, oi.model, oi.quantity,"
                + " COALESCE(ls.status, 0) ship_status,"
                + " EXISTS (SELECT 1 FROM inv_move m WHERE m.tenant_id = o.tenant_id AND m.doc_type = 'OB' AND m.doc_id = o.id) posted"
                + " FROM outbound_order_item oi JOIN outbound_order o ON o.id = oi.outbound_id AND o.tenant_id = oi.tenant_id"
                + " LEFT JOIN logistics_shipment ls ON ls.id = o.logistics_id AND ls.tenant_id = o.tenant_id"
                + " WHERE oi.tenant_id = ? AND o.status = 3 AND o.deleted_at IS NULL AND oi.deleted_at IS NULL"
                + " ORDER BY o.id, oi.id", tenant);
        List<Slot> moved = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Long soItemId = longOf(r.get("so_item_id"));
            Long forwarderId = longOf(r.get("forwarder_id"));
            if (soItemId == null || forwarderId == null) {
                continue;
            }
            long fw = warehouses.forwarder(forwarderId).getId();
            boolean shipped = ((Number) r.get("ship_status")).intValue() == 2;
            boolean posted = ((Number) r.get("posted")).intValue() == 1;
            int left = ((Number) r.get("quantity")).intValue();
            List<Slot> candidates = new ArrayList<>();
            slots.stream().filter(s -> Objects.equals(s.soItemId, soItemId) && s.warehouseId == fw).forEach(candidates::add);
            slots.stream().filter(s -> Objects.equals(s.soItemId, soItemId) && s.warehouseId == own).forEach(candidates::add);
            for (Slot s : candidates) {
                if (left == 0) {
                    break;
                }
                int n = Math.min(left, s.good);
                if (n <= 0) {
                    continue;
                }
                s.good -= n;
                left -= n;
                if (!shipped && !posted) {
                    Slot f = new Slot(s.batchId, s.productId, s.soItemId, fw);
                    f.good = n;
                    moved.add(f);
                }
            }
            if (left > 0 && !shipped && !posted) {
                negatives.add("出库单 " + r.get("ob_no") + " " + r.get("model") + " 已交货代缺入库 " + left + " 个");
            }
        }
        // 同一批次同一货代仓合并
        Map<String, Slot> merged = new LinkedHashMap<>();
        for (Slot f : moved) {
            merged.merge(f.batchId + ":" + f.warehouseId + ":" + f.soItemId, f, (a, b) -> {
                a.good += b.good;
                return a;
            });
        }
        slots.addAll(merged.values());
    }

    /** 退货入库单：未处理、转闲置未处置、退供应商待寄出的仍在库（没有流水的已验收退货入库单） */
    private void returnSlots(int tenant, long own, List<Slot> slots, List<String> negatives) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT i.id, i.receipt_id, r.receipt_no, r.received_date, i.product_id, i.so_item_id,"
                + " COALESCE(i.received_qty, 0) received, COALESCE(i.qualified_qty, 0) qualified"
                + " FROM return_receipt_item i JOIN return_receipt r ON r.id = i.receipt_id AND r.tenant_id = i.tenant_id"
                + " WHERE i.tenant_id = ? AND r.status IN (2, 3) AND r.deleted_at IS NULL AND i.deleted_at IS NULL"
                + " AND NOT EXISTS (SELECT 1 FROM inv_move m WHERE m.tenant_id = r.tenant_id AND m.doc_type = 'RR' AND m.doc_id = r.id)"
                + " ORDER BY r.id, i.id", tenant);
        if (rows.isEmpty()) {
            return;
        }
        Map<Long, Integer> seq = new HashMap<>();
        Map<Long, Long> first = new HashMap<>();
        jdbc.query("SELECT id, receipt_id FROM return_receipt_item WHERE tenant_id = ? ORDER BY receipt_id, id", rs -> {
            seq.put(rs.getLong(1), seq.size());
            first.putIfAbsent(rs.getLong(2), rs.getLong(1));
        }, tenant);
        Map<Long, BigDecimal> cost = new HashMap<>();
        jdbc.query("SELECT so_item_id, net_price_cny FROM purchase_order_item WHERE tenant_id = ? AND so_item_id IS NOT NULL"
                + " AND deleted_at IS NULL ORDER BY id", rs -> {
            cost.put(rs.getLong(1), rs.getBigDecimal(2));
        }, tenant);
        Map<Long, String> grades = hooks.grades(rows.stream().map(r -> longOf(r.get("so_item_id"))).toList());
        // 去向：退供应商按采购退货单状态；转闲置按闲置库存状态；报废已出
        Map<Long, int[]> out = new HashMap<>();
        Map<Long, List<long[]>> pending = new HashMap<>();
        for (Map<String, Object> d : jdbc.queryForList("SELECT d.receipt_item_id, d.disposition, d.quantity, COALESCE(pr.status, 0) pr_status,"
                + " COALESCE(h.status, 0) hold_status, (SELECT pi.id FROM purchase_return_item pi WHERE pi.tenant_id = d.tenant_id"
                + "   AND pi.disposition_id = d.id AND pi.deleted_at IS NULL LIMIT 1) pr_item_id"
                + " FROM sales_return_disposition d"
                + " LEFT JOIN purchase_return pr ON pr.id = d.purchase_return_id AND pr.tenant_id = d.tenant_id"
                + " LEFT JOIN stock_hold h ON h.id = d.hold_id AND h.tenant_id = d.tenant_id"
                + " WHERE d.tenant_id = ? AND d.deleted_at IS NULL", tenant)) {
            Long itemId = longOf(d.get("receipt_item_id"));
            int q = ((Number) d.get("quantity")).intValue();
            int disp = ((Number) d.get("disposition")).intValue();
            int prStatus = ((Number) d.get("pr_status")).intValue();
            boolean gone = switch (disp) {
                case 1 -> prStatus != PR_PENDING && prStatus != 4;
                case 2 -> ((Number) d.get("hold_status")).intValue() != 1;
                default -> true;
            };
            if (gone) {
                out.computeIfAbsent(itemId, k -> new int[1])[0] += q;
            } else if (disp == 1 && prStatus == PR_PENDING && d.get("pr_item_id") != null) {
                pending.computeIfAbsent(itemId, k -> new ArrayList<>()).add(new long[]{longOf(d.get("pr_item_id")), q});
            }
        }
        for (Map<String, Object> r : rows) {
            Long productId = longOf(r.get("product_id"));
            if (productId == null) {
                continue;
            }
            long itemId = ((Number) r.get("id")).longValue();
            long receiptId = ((Number) r.get("receipt_id")).longValue();
            int received = ((Number) r.get("received")).intValue();
            int qualified = ((Number) r.get("qualified")).intValue();
            int inStock = received - out.getOrDefault(itemId, new int[1])[0];
            if (inStock < 0) {
                negatives.add("退货入库单 " + r.get("receipt_no") + " 行 " + itemId + " 推算 " + inStock);
                continue;
            }
            if (inStock == 0) {
                continue;
            }
            Long soItemId = longOf(r.get("so_item_id"));
            InvBatchDO b = warehouses.batchOf(InvConstants.BATCH_RETURN, itemId);
            if (b == null) {
                int n = (int) (seq.get(itemId) - seq.get(first.get(receiptId))) + 1;
                b = warehouses.newBatch(r.get("receipt_no") + "-" + n, productId, grades.getOrDefault(soItemId, ""), InvConstants.BATCH_RETURN,
                        receiptId, itemId, null, soItemId, toDate(r.get("received_date")), Price.cny(cost.get(soItemId)));
            }
            Slot s = new Slot(b.getId(), productId, null, own);
            s.good = Math.min(inStock, qualified);
            s.bad = inStock - s.good;
            // 待寄出的退供应商：不良优先占
            for (long[] p : pending.getOrDefault(itemId, List.of())) {
                int left = (int) p[1];
                int fromBad = Math.min(left, s.bad - s.badRes.values().stream().mapToInt(Integer::intValue).sum());
                if (fromBad > 0) {
                    s.badRes.merge(Target.returnItem(p[0]), fromBad, Integer::sum);
                    left -= fromBad;
                }
                int fromGood = Math.min(left, s.good - s.reservedGood());
                if (fromGood > 0) {
                    s.goodRes.merge(Target.returnItem(p[0]), fromGood, Integer::sum);
                }
            }
            slots.add(s);
        }
    }

    /** 订单预占：需要 = 现在的数量（取消为 0）− 已出运；先占货代仓，再占本型号行的批次 */
    private void reserveForOrders(int tenant, long own, List<Slot> slots) {
        Set<Long> soItems = new HashSet<>();
        slots.stream().map(s -> s.soItemId).filter(Objects::nonNull).forEach(soItems::add);
        if (soItems.isEmpty()) {
            return;
        }
        Map<Long, int[]> shipped = sumBy("SELECT oi.so_item_id, SUM(oi.quantity) FROM outbound_order_item oi"
                + " JOIN outbound_order o ON o.id = oi.outbound_id AND o.tenant_id = oi.tenant_id"
                + " JOIN logistics_shipment ls ON ls.id = o.logistics_id AND ls.tenant_id = o.tenant_id"
                + " WHERE oi.tenant_id = ? AND ls.status = 2 AND o.deleted_at IS NULL AND oi.deleted_at IS NULL GROUP BY oi.so_item_id", tenant);
        Map<Long, Integer> need = new HashMap<>();
        reservations.need(new ArrayList<>(soItems)).forEach((k, v) -> need.put(k, Math.max(0, v - shipped.getOrDefault(k, new int[1])[0])));
        List<Slot> ordered = new ArrayList<>();
        slots.stream().filter(s -> s.warehouseId != own).forEach(ordered::add);
        slots.stream().filter(s -> s.warehouseId == own).forEach(ordered::add);
        for (Slot s : ordered) {
            if (s.soItemId == null) {
                continue;
            }
            int n = Math.min(need.getOrDefault(s.soItemId, 0), s.good - s.reservedGood());
            if (n > 0) {
                s.goodRes.merge(Target.soItem(s.soItemId), n, Integer::sum);
                need.merge(s.soItemId, -n, Integer::sum);
            }
        }
    }

    // ---------------------------------------------------------------- 工具

    private Map<Long, int[]> sumBy(String sql, int tenant) {
        Map<Long, int[]> out = new HashMap<>();
        jdbc.query(sql, rs -> {
            out.computeIfAbsent(rs.getLong(1), k -> new int[1])[0] += rs.getInt(2);
        }, tenant);
        return out;
    }

    private static Long longOf(Object v) {
        return v == null ? null : ((Number) v).longValue();
    }

    private static LocalDate toDate(Object v) {
        if (v instanceof Date d) {
            return d.toLocalDate();
        }
        if (v instanceof LocalDate d) {
            return d;
        }
        return LocalDate.now();
    }
}
