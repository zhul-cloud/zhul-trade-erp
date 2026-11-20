package com.zhul.erp.modules.inventory;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 库存一致性核对（openspec inventory-foundation tasks 3.9）：跑完流程后比对
 * ①余额结存 = 流水合计；②余额预占 = 预占表合计；③预占 = 带该对象的流水合计；④没有负数、预占不超过结存；
 * ⑤每张有效采购入库单的入库流水 = 合格 + 不良；⑥每个型号行的销售出库 = 已出运出运单里的销售出库单数量。
 * 只看指定租户，单据核对只看关联了商品的行。
 */
public final class InventoryConsistency {

    private InventoryConsistency() {
    }

    public static void check(JdbcTemplate jdbc, int tenant) {
        List<String> bad = new ArrayList<>();
        // ① 结存 = 流水（预占、释放不动结存）
        bad.addAll(jdbc.queryForList("SELECT CONCAT('结存≠流水 ', k, '：', b, ' vs ', m) FROM ("
                + " SELECT k, SUM(b) b, SUM(m) m FROM ("
                + "  SELECT CONCAT_WS('/', warehouse_id, product_id, batch_id, stock_status) k, on_hand b, 0 m FROM inv_balance WHERE tenant_id = ?"
                + "  UNION ALL SELECT CONCAT_WS('/', warehouse_id, product_id, batch_id, stock_status), 0, quantity FROM inv_move"
                + "  WHERE tenant_id = ? AND op_type NOT IN (7, 8)) x GROUP BY k) y WHERE b <> m", String.class, tenant, tenant));
        // ② 余额预占 = 预占表
        bad.addAll(jdbc.queryForList("SELECT CONCAT('余额预占≠预占表 ', k, '：', b, ' vs ', r) FROM ("
                + " SELECT k, SUM(b) b, SUM(r) r FROM ("
                + "  SELECT CONCAT_WS('/', warehouse_id, product_id, batch_id, stock_status) k, reserved b, 0 r FROM inv_balance WHERE tenant_id = ?"
                + "  UNION ALL SELECT CONCAT_WS('/', warehouse_id, product_id, batch_id, stock_status), 0, quantity FROM inv_reservation"
                + "  WHERE tenant_id = ?) x GROUP BY k) y WHERE b <> r", String.class, tenant, tenant));
        // ③ 预占 = 带对象的流水（商品合并后预占改记主商品，按对象 + 仓库 + 批次 + 状态比）
        bad.addAll(jdbc.queryForList("SELECT CONCAT('预占≠流水 ', k, '：', r, ' vs ', m) FROM ("
                + " SELECT k, SUM(r) r, SUM(m) m FROM ("
                + "  SELECT CONCAT_WS('/', target_type, target_id, warehouse_id, batch_id, stock_status) k, quantity r, 0 m FROM inv_reservation"
                + "  WHERE tenant_id = ?"
                + "  UNION ALL SELECT CONCAT_WS('/', target_type, target_id, warehouse_id, batch_id, stock_status), 0, quantity FROM inv_move"
                + "  WHERE tenant_id = ? AND target_type IS NOT NULL) x GROUP BY k) y WHERE r <> m", String.class, tenant, tenant));
        // ④ 没有负数，预占不超过结存
        bad.addAll(jdbc.queryForList("SELECT CONCAT('余额不合法 ', id, '：', on_hand, '/', reserved) FROM inv_balance"
                + " WHERE tenant_id = ? AND (on_hand < 0 OR reserved < 0 OR reserved > on_hand)", String.class, tenant));
        bad.addAll(jdbc.queryForList("SELECT CONCAT('预占为负 ', id) FROM inv_reservation WHERE tenant_id = ? AND quantity < 0",
                String.class, tenant));
        // ⑤ 有效采购入库单：入库流水 = 合格 + 不良；已冲销的为 0
        bad.addAll(jdbc.queryForList("SELECT CONCAT('采购入库≠入库单 ', r.gr_no, '：', COALESCE(m.q, 0), ' vs ', d.q) FROM purchase_receipt r"
                + " JOIN (SELECT receipt_id, SUM(CASE WHEN pr.status = 1 THEN COALESCE(i.qualified_qty, 0) + COALESCE(i.defective_qty, 0) ELSE 0 END) q"
                + "   FROM purchase_receipt_item i JOIN purchase_receipt pr ON pr.id = i.receipt_id"
                + "   WHERE i.tenant_id = ? AND i.deleted_at IS NULL AND i.product_id IS NOT NULL GROUP BY receipt_id) d ON d.receipt_id = r.id"
                + " LEFT JOIN (SELECT doc_id, SUM(quantity) q FROM inv_move WHERE tenant_id = ? AND doc_type = 'GR'"
                + "   AND biz_type IN ('PURCHASE_IN', 'PURCHASE_IN_REVERSE') AND op_type IN (1, 2) GROUP BY doc_id) m ON m.doc_id = r.id"
                + " WHERE r.tenant_id = ? AND COALESCE(m.q, 0) <> d.q", String.class, tenant, tenant, tenant));
        // ⑥ 型号行销售出库 = 已出运出运单里的销售出库单数量
        bad.addAll(jdbc.queryForList("SELECT CONCAT('销售出库≠出运 ', d.so_item_id, '：', COALESCE(m.q, 0), ' vs ', d.q) FROM"
                + " (SELECT oi.so_item_id, SUM(oi.quantity) q FROM outbound_order_item oi JOIN outbound_order o ON o.id = oi.outbound_id"
                + "   JOIN logistics_shipment s ON s.id = o.logistics_id"
                + "   WHERE oi.tenant_id = ? AND oi.deleted_at IS NULL AND o.deleted_at IS NULL AND s.status = 2 AND oi.product_id IS NOT NULL"
                + "   GROUP BY oi.so_item_id) d"
                + " LEFT JOIN (SELECT target_id, -SUM(quantity) q FROM inv_move WHERE tenant_id = ? AND target_type = 1"
                + "   AND biz_type IN ('SALES_OUT', 'SALES_OUT_UNDO') AND op_type IN (1, 2) GROUP BY target_id) m ON m.target_id = d.so_item_id"
                + " WHERE COALESCE(m.q, 0) <> d.q", String.class, tenant, tenant));
        assertEquals(List.of(), bad, "库存一致性");
    }
}
