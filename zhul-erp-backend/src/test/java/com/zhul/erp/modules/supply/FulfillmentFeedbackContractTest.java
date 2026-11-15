package com.zhul.erp.modules.supply;

import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.supply.support.FulfillmentFeedback;
import com.zhul.erp.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 供应链第三期履约异常回写（tasks 4.4）：延期识别与去重、到货不良降级并提醒、人定下一步未到期不覆盖、
 * 没有需求对象跳过、迁移订单和有入库的订单不算延期、取消。
 */
class FulfillmentFeedbackContractTest extends IntegrationTestBase {

    @Autowired private FulfillmentFeedback feedback;

    private long brandId;

    @BeforeEach
    void data() {
        cleanup();
        jdbc.update("insert into product_brand (tenant_id, brand_name, status) values (0, 'ITFFBRAND', 1)");
        brandId = jdbc.queryForObject("select id from product_brand where brand_name = 'ITFFBRAND'", Long.class);
    }

    @AfterEach
    void cleanup() {
        jdbc.update("delete from sc_followup_log where tenant_id = 0");
        jdbc.update("delete from sc_demand where tenant_id = 0");
        jdbc.update("delete from document_log where tenant_id = 0 and doc_type = 'SC'");
        jdbc.update("delete from receiving_discrepancy where model like 'ITFF-%'");
        jdbc.update("delete from purchase_receipt where po_id in (select id from purchase_order where po_no like 'ITFFPO%')");
        jdbc.update("delete from supplier_shipment where po_id in (select id from purchase_order where po_no like 'ITFFPO%')");
        jdbc.update("delete from purchase_order_item where po_id in (select id from purchase_order where po_no like 'ITFFPO%')");
        jdbc.update("delete from purchase_order where po_no like 'ITFFPO%'");
        jdbc.update("delete from product_brand where brand_name = 'ITFFBRAND'");
    }

    @Test
    void delayDowngradesAndReminds_once() {
        long d = demand("ITFF-001", 4, null, null);
        long line = po("ITFFPO1", 2, 0, LocalDate.now().minusDays(5), "ITFF-001");
        scan();
        Map<String, Object> row = row(d);
        assertEquals(3, num(row, "coverage_status"), "可直接报价降为需确认");
        assertEquals(5, num(row, "reminder_type"), "履约异常提醒");
        assertEquals("采购 ITFFPO1 延期，确认渠道是否可靠", row.get("next_action"));
        assertEquals(LocalDate.now(), ((java.sql.Date) row.get("next_action_date")).toLocalDate());
        List<Map<String, Object>> logs = logs(d, 6);
        assertEquals(1, logs.size());
        assertEquals(line, ((Number) logs.get(0).get("source_id")).longValue());
        assertEquals("ITFFPO1", logs.get(0).get("source_doc_no"));
        assertTrue(((String) logs.get(0).get("content")).contains("已过 5 天，没有供应商发货记录"), logs.toString());
        assertEquals(1, jdbc.queryForObject("select count(*) from document_log where tenant_id = 0 and doc_type = 'SC'"
                + " and doc_id = ? and content like '%履约异常%'", Integer.class, d), "覆盖状态变化写操作日志");

        // 人改回可直接报价后再扫描：同一采购行不再记录、不再降级
        jdbc.update("update sc_demand set coverage_status = 4, next_action = '', next_action_source = 0, reminder_type = 0 where id = ?", d);
        scan();
        assertEquals(1, logs(d, 6).size(), "重复执行不重复记录");
        assertEquals(4, num(row(d), "coverage_status"));
    }

    @Test
    void migratedShippedReceivedAndOutOfWindow_notDelayed() {
        long d = demand("ITFF-002", 4, null, null);
        po("ITFFPO2", 2, 1, LocalDate.now().minusDays(5), "ITFF-002");
        long shipped = poId(po("ITFFPO3", 2, 0, LocalDate.now().minusDays(5), "ITFF-002"));
        jdbc.update("insert into supplier_shipment (tenant_id, sd_no, po_id, status) values (0, 'ITFFSD1', ?, 1)", shipped);
        long received = poId(po("ITFFPO4", 2, 0, LocalDate.now().minusDays(5), "ITFF-002"));
        jdbc.update("insert into purchase_receipt (tenant_id, gr_no, po_id, status) values (0, 'ITFFGR1', ?, 1)", received);
        po("ITFFPO5", 2, 0, LocalDate.now().minusDays(31), "ITFF-002");
        po("ITFFPO6", 2, 0, LocalDate.now(), "ITFF-002");
        // 发货记录已作废的仍算延期
        long voided = poId(po("ITFFPO7", 2, 0, LocalDate.now().minusDays(2), "ITFF-002"));
        jdbc.update("insert into supplier_shipment (tenant_id, sd_no, po_id, status) values (0, 'ITFFSD2', ?, 3)", voided);
        scan();
        List<Map<String, Object>> logs = logs(d, 6);
        assertEquals(1, logs.size(), "只有发货记录已作废的那张算延期：" + logs);
        assertEquals("ITFFPO7", logs.get(0).get("source_doc_no"));
    }

    @Test
    void defectiveArrival_keepsManualNextAction_andCancelRecorded() {
        LocalDate later = LocalDate.now().plusDays(3);
        long d = demand("ITFF-003", 4, "周五回访供应商", later);
        long poId = poId(po("ITFFPO8", 2, 0, LocalDate.now().minusDays(40), "ITFF-003"));
        jdbc.update("insert into receiving_discrepancy (tenant_id, po_id, model, brand, type, quantity, status)"
                + " values (0, ?, 'ITFF-003', 'ITFFBRAND', 2, 2, 1)", poId);
        jdbc.update("insert into receiving_discrepancy (tenant_id, po_id, model, brand, type, quantity, status)"
                + " values (0, ?, 'ITFF-003', 'ITFFBRAND', 3, 1, 1)", poId);
        po("ITFFPO9", 3, 0, null, "ITFF-003");
        jdbc.update("update purchase_order set ordered_at = now() - interval 3 day, cancelled_at = now() - interval 1 day,"
                + " cancel_reason = '供应商无货' where po_no = 'ITFFPO9'");
        scan();
        Map<String, Object> row = row(d);
        assertEquals(3, num(row, "coverage_status"));
        assertEquals("周五回访供应商", row.get("next_action"), "人定的下一步还没到期，不覆盖");
        assertEquals(1, num(row, "next_action_source"));
        assertEquals(1, logs(d, 8).size(), "不良登记一次，多发不算");
        assertTrue(((String) logs(d, 8).get(0).get("content")).contains("到货不良 2 件"));
        assertEquals(1, logs(d, 7).size(), "取消登记一次");
        assertTrue(((String) logs(d, 7).get(0).get("content")).contains("供应商无货"));
        assertEquals(2, logs(d, 4).size(), "两条提醒都只在时间线留痕");
    }

    @Test
    void noDemandOrUnknownBrand_skipped() {
        po("ITFFPO10", 2, 0, LocalDate.now().minusDays(3), "ITFF-404");
        jdbc.update("update purchase_order_item set brand = '不认识的品牌' where model = 'ITFF-404'");
        FulfillmentFeedback.Summary s = scan();
        assertTrue(s.found() >= 1);
        assertEquals(0, jdbc.queryForObject("select count(*) from sc_followup_log where tenant_id = 0", Integer.class));
    }

    private FulfillmentFeedback.Summary scan() {
        FulfillmentFeedback.Summary[] out = new FulfillmentFeedback.Summary[1];
        TenantContext.runAs(0, () -> out[0] = feedback.scan(LocalDate.now()));
        return out[0];
    }

    private long demand(String model, int coverage, String next, LocalDate nextDate) {
        String key = model.toLowerCase().replace("-", "");
        jdbc.update("insert into sc_demand (tenant_id, brand_id, model_key, model, scope_key, status, priority, coverage_status,"
                        + " evidence_date, next_action, next_action_date, next_action_source) values (0, ?, ?, ?, ?, 2, 3, ?, curdate(), ?, ?, ?)",
                brandId, key, model, "m:" + key, coverage, next == null ? "" : next, nextDate, next == null ? 0 : 1);
        return jdbc.queryForObject("select id from sc_demand where tenant_id = 0 and model_key = ?", Long.class, key);
    }

    /** 采购单 + 一行，返回采购行ID */
    private long po(String no, int status, int migrated, LocalDate expected, String model) {
        jdbc.update("insert into purchase_order (tenant_id, po_no, status, migrated, expected_ship_date, ordered_at, order_date)"
                + " values (0, ?, ?, ?, ?, now(), curdate())", no, status, migrated, expected);
        long id = jdbc.queryForObject("select id from purchase_order where po_no = ?", Long.class, no);
        jdbc.update("insert into purchase_order_item (tenant_id, po_id, model, brand, quantity) values (0, ?, ?, 'ITFFBRAND', 3)",
                id, model);
        return jdbc.queryForObject("select id from purchase_order_item where po_id = ?", Long.class, id);
    }

    private long poId(long lineId) {
        return jdbc.queryForObject("select po_id from purchase_order_item where id = ?", Long.class, lineId);
    }

    private Map<String, Object> row(long id) {
        return jdbc.queryForMap("select * from sc_demand where id = ?", id);
    }

    private List<Map<String, Object>> logs(long demand, int sourceType) {
        return jdbc.queryForList("select * from sc_followup_log where tenant_id = 0 and source_type = ? and json_contains(demand_ids, ?)",
                sourceType, String.valueOf(demand));
    }

    private static int num(Map<String, Object> m, String k) {
        return ((Number) m.get(k)).intValue();
    }
}
