package com.zhul.erp.modules.crm.followup;

import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.crm.followup.service.FollowupReconcileJob;
import com.zhul.erp.modules.crm.followup.support.FollowupSync;
import com.zhul.erp.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 订单履约 → 售后评价的同步（tasks 2.5、3.3）：连测试库执行真实 SQL，按单据变化逐步核对阶段、预计送达与转入售后。
 * 型号进度由采购、入库、出库、出运模块自动推进，这里直接改进度码模拟。
 */
class OrderFollowupSyncTest extends IntegrationTestBase {

    private static final long OWNER = 99000002L;
    private static final long C1 = 99200001L;
    private static final long SO = 99210001L;
    private static final long ITEM1 = 99211001L;
    private static final long ITEM2 = 99211002L;

    @Autowired private FollowupSync followupSync;
    @Autowired private FollowupReconcileJob reconcileJob;

    @BeforeEach
    void setUp() {
        cleanup();
        jdbc.update("insert into customer (id, tenant_id, customer_code, name, country, owner_id) values (?, 0, 'CU1', 'Müller GmbH',"
                + " 'Germany', ?)", C1, OWNER);
        jdbc.update("insert into sales_order (id, tenant_id, so_no, customer_id, owner_id, currency_code, total_amount, total_amount_cny,"
                + " status, progress_code, create_time) values (?, 0, 'SO1', ?, ?, 'USD', 23800, 168980, 1, 'PENDING_PURCHASE', ?)",
                SO, C1, OWNER, LocalDateTime.of(2026, 10, 25, 9, 0));
        for (long id : new long[]{ITEM1, ITEM2}) {
            jdbc.update("insert into sales_order_item (id, tenant_id, so_id, model, quantity, progress_code) values (?, 0, ?, ?, 2,"
                    + " 'PENDING_PURCHASE')", id, SO, "M" + id);
            jdbc.update("insert into purchase_requirement (tenant_id, so_id, so_item_id, model, quantity, status) values (0, ?, ?, 'M', 2, 1)",
                    SO, id);
        }
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    private void cleanup() {
        for (String t : new String[]{"followup_item", "customer_followup", "after_sales", "logistics_shipment", "outbound_order_item",
                "outbound_order", "purchase_order_item", "purchase_order", "purchase_requirement", "sales_order_item", "sales_order"}) {
            jdbc.update("delete from " + t + " where tenant_id = 0");
        }
        jdbc.update("delete from customer where tenant_id = 0");
    }

    private void sync(LocalDate today) {
        TenantContext.runAs(0, () -> followupSync.resync(C1, today, false));
    }

    private Map<String, Object> row(int board) {
        List<Map<String, Object>> rows = jdbc.queryForList("select * from followup_item where tenant_id = 0 and board = ? and target_id = ?",
                board, SO);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private void progress(long item, String code) {
        jdbc.update("update sales_order_item set progress_code = ? where id = ?", code, item);
    }

    @Test
    void orderFlowsFromFulfilToAfterSales() {
        LocalDate d = LocalDate.of(2026, 10, 25);
        sync(d);
        Map<String, Object> f = row(3);
        assertEquals("F1", f.get("stage_code"));
        assertEquals("确认订单要点，告诉客户预计交期", f.get("next_action"));
        assertEquals(java.sql.Date.valueOf(d), f.get("next_action_date"));

        // 采购单下单（第 2 个型号），第 1 个型号还没下
        jdbc.update("insert into purchase_order (id, tenant_id, po_no, supplier_id, status, ordered_at, expected_ship_date)"
                + " values (99220001, 0, 'PO1', 0, 2, ?, ?)", LocalDateTime.of(2026, 10, 26, 10, 0), LocalDate.of(2026, 11, 2));
        jdbc.update("insert into purchase_order_item (id, tenant_id, po_id, so_id, so_item_id, quantity) values (99221001, 0, 99220001, ?, ?, 2)",
                SO, ITEM2);
        progress(ITEM2, "ORDERED");
        sync(LocalDate.of(2026, 10, 26));
        f = row(3);
        assertEquals("F2", f.get("stage_code"));
        assertEquals("ordered", f.get("node_code"));
        assertEquals(2, ((Number) f.get("part_count")).intValue());

        // 第 1 个型号也下单；超过预计发货日没到仓 → 异常
        jdbc.update("insert into purchase_order_item (id, tenant_id, po_id, so_id, so_item_id, quantity) values (99221002, 0, 99220001, ?, ?, 2)",
                SO, ITEM1);
        progress(ITEM1, "ORDERED");
        sync(LocalDate.of(2026, 11, 4));
        f = row(3);
        assertEquals("F3", f.get("stage_code"));
        assertEquals(1, ((Number) f.get("exception_flags")).intValue());
        assertEquals("exception", f.get("node_code"));

        // 入库 → 出库 → 出运
        progress(ITEM1, "RECEIVED");
        progress(ITEM2, "RECEIVED");
        sync(LocalDate.of(2026, 11, 5));
        f = row(3);
        assertEquals("F4", f.get("stage_code"));
        assertEquals(0, ((Number) f.get("exception_flags")).intValue());
        jdbc.update("insert into logistics_shipment (id, tenant_id, sh_no, customer_id, status, carrier, waybill_no, shipped_date)"
                + " values (99230001, 0, 'SH1', ?, 2, 'DHL', '1234567890', ?)", C1, LocalDate.of(2026, 11, 6));
        jdbc.update("insert into outbound_order (id, tenant_id, ob_no, so_id, customer_id, status, logistics_id) values (99240001, 0, 'OB1',"
                + " ?, ?, 3, 99230001)", SO, C1);
        progress(ITEM1, "SHIPPED");
        progress(ITEM2, "SHIPPED");
        sync(LocalDate.of(2026, 11, 6));
        f = row(3);
        assertEquals("F6", f.get("stage_code"));
        assertEquals(java.sql.Date.valueOf(LocalDate.of(2026, 11, 11)), f.get("eta_date"));
        assertEquals("DHL · 按 5 天估算", f.get("eta_note"));
        assertEquals("把运单号和发货照片发给客户", f.get("next_action"));
        assertNull(row(4));

        // 到预计送达日 → 转入售后评价 A1
        sync(LocalDate.of(2026, 11, 11));
        assertNotNull(row(3).get("closed_at"));
        Map<String, Object> a = row(4);
        assertEquals("A1", a.get("stage_code"));
        assertEquals("问客户是否收到，数量外观对不对", a.get("next_action"));
        assertEquals(java.sql.Date.valueOf(LocalDate.of(2026, 11, 11)), a.get("eta_first"));
        assertTrue(jdbc.queryForObject("select count(*) from customer_followup where tenant_id = 0 and title like '%转入售后评价%'",
                Integer.class) > 0);

        // 登记售后单 → A3；结案 → 签收后的 A5
        jdbc.update("update followup_item set signed_date = '2026-11-12' where board = 4 and target_id = ?", SO);
        jdbc.update("insert into after_sales (id, tenant_id, as_no, customer_id, so_id, status, next_action, next_update_at, create_time)"
                + " values (99250001, 0, 'AS1', ?, ?, 1, '补发出运后告诉客户运单号', '2026-11-14 09:00:00', '2026-11-12 10:00:00')", C1, SO);
        sync(LocalDate.of(2026, 11, 12));
        a = row(4);
        assertEquals("A3", a.get("stage_code"));
        assertEquals(4, ((Number) a.get("next_action_source")).intValue());
        assertEquals("补发出运后告诉客户运单号", a.get("next_action"));
        jdbc.update("update after_sales set status = 3, closed_at = '2026-11-15 10:00:00' where id = 99250001");
        sync(LocalDate.of(2026, 11, 15));
        a = row(4);
        assertEquals("A5", a.get("stage_code"));
        assertEquals(1, ((Number) a.get("next_action_source")).intValue());
    }

    @Test
    void completedOrder_goesStraightToA2() {
        sync(LocalDate.of(2026, 10, 25));
        progress(ITEM1, "SHIPPED");
        progress(ITEM2, "SHIPPED");
        jdbc.update("update sales_order set progress_code = 'COMPLETED', completed_at = '2026-11-02 10:00:00' where id = ?", SO);
        sync(LocalDate.of(2026, 11, 2));
        assertNotNull(row(3).get("closed_at"));
        Map<String, Object> a = row(4);
        assertEquals("A2", a.get("stage_code"));
        assertEquals(java.sql.Date.valueOf(LocalDate.of(2026, 11, 2)), a.get("signed_date"));
    }

    @Test
    void cancelledOrder_leavesBoard() {
        sync(LocalDate.of(2026, 10, 25));
        jdbc.update("update sales_order set status = 2 where id = ?", SO);
        sync(LocalDate.of(2026, 10, 26));
        assertNotNull(row(3).get("closed_at"));
        assertEquals(4, ((Number) row(3).get("close_reason")).intValue());
    }

    @Test
    void bootstrap_oldShippedOrders() {
        progress(ITEM1, "SHIPPED");
        progress(ITEM2, "SHIPPED");
        jdbc.update("insert into logistics_shipment (id, tenant_id, sh_no, customer_id, status, carrier, shipped_date)"
                + " values (99230001, 0, 'SH1', ?, 2, 'DHL', '2026-09-01')", C1);
        jdbc.update("insert into outbound_order (id, tenant_id, ob_no, so_id, customer_id, status, logistics_id) values (99240001, 0, 'OB1',"
                + " ?, ?, 3, 99230001)", SO, C1);
        // 预计送达 09-06，上线日 11-04：超过 30 天，不生成
        sync(LocalDate.of(2026, 11, 4));
        assertNull(row(3));
        assertNull(row(4));
        // 预计送达 10-20，在 30 天内：直接进 A1
        jdbc.update("update logistics_shipment set shipped_date = '2026-10-15' where id = 99230001");
        sync(LocalDate.of(2026, 11, 4));
        assertNull(row(3));
        assertEquals("A1", row(4).get("stage_code"));
        assertEquals(new BigDecimal("23800.00"), row(4).get("amount"));
    }

    @Test
    void reconcileJob_bootstrapsOrderRows() {
        LocalDate today = LocalDate.now();
        // SO1：20 天前 DHL 出运 → 预计送达 15 天前，补生成到 A1
        progress(ITEM1, "SHIPPED");
        progress(ITEM2, "SHIPPED");
        jdbc.update("insert into logistics_shipment (id, tenant_id, sh_no, customer_id, status, carrier, shipped_date)"
                + " values (99230001, 0, 'SH1', ?, 2, 'DHL', ?)", C1, today.minusDays(20));
        jdbc.update("insert into outbound_order (id, tenant_id, ob_no, so_id, customer_id, status, logistics_id) values (99240001, 0, 'OB1',"
                + " ?, ?, 3, 99230001)", SO, C1);
        // SO2：60 天前出运 → 预计送达已过 30 天，不生成；SO3：还没采购 → F1
        long so2 = 99210002L;
        long so3 = 99210003L;
        jdbc.update("insert into sales_order (id, tenant_id, so_no, customer_id, owner_id, currency_code, total_amount, total_amount_cny,"
                + " status, progress_code) values (?, 0, 'SO2', ?, ?, 'USD', 100, 710, 1, 'SHIPPED'), (?, 0, 'SO3', ?, ?, 'USD', 100, 710, 1,"
                + " 'PENDING_PURCHASE')", so2, C1, OWNER, so3, C1, OWNER);
        jdbc.update("insert into sales_order_item (id, tenant_id, so_id, model, quantity, progress_code) values (99211003, 0, ?, 'M3', 1,"
                + " 'SHIPPED'), (99211004, 0, ?, 'M4', 1, 'PENDING_PURCHASE')", so2, so3);
        jdbc.update("insert into purchase_requirement (tenant_id, so_id, so_item_id, model, quantity, status) values (0, ?, 99211004, 'M4',"
                + " 1, 1)", so3);
        jdbc.update("insert into logistics_shipment (id, tenant_id, sh_no, customer_id, status, carrier, shipped_date)"
                + " values (99230002, 0, 'SH2', ?, 2, 'DHL', ?)", C1, today.minusDays(60));
        jdbc.update("insert into outbound_order (id, tenant_id, ob_no, so_id, customer_id, status, logistics_id) values (99240002, 0, 'OB2',"
                + " ?, ?, 3, 99230002)", so2, C1);

        TenantContext.runAs(0, () -> reconcileJob.reconcile(0));

        assertEquals("A1", row(4).get("stage_code"));
        assertNull(row(3));
        assertEquals(0, jdbc.queryForObject("select count(*) from followup_item where tenant_id = 0 and target_type = 5 and target_id = ?",
                Integer.class, so2));
        assertEquals("F1", jdbc.queryForObject("select stage_code from followup_item where tenant_id = 0 and board = 3 and target_id = ?",
                String.class, so3));
        assertEquals(2, jdbc.queryForObject("select count(*) from followup_item where tenant_id = 0 and target_type = 5", Integer.class));
    }
}
