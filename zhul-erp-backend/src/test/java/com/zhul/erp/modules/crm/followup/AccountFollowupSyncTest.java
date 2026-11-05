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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 客户经营的同步（第三期 tasks 2.x）：连测试库执行真实 SQL，核对生成条件、采购规律、暂停、沉睡与激活、移出。
 * 订单记为历史迁移（migrated = 1），不生成订单履约对象，免得暂停客户经营提醒。
 */
class AccountFollowupSyncTest extends IntegrationTestBase {

    private static final long OWNER = 99000002L;
    private static final long C1 = 99300001L;
    private static final LocalDate TODAY = LocalDate.now();

    @Autowired private FollowupSync followupSync;
    @Autowired private FollowupReconcileJob reconcileJob;

    private long nextOrderId = 99310001L;

    @BeforeEach
    void setUp() {
        cleanup();
        jdbc.update("insert into customer (id, tenant_id, customer_code, name, country, owner_id, biz_grade) values (?, 0, 'CU300001',"
                + " 'Müller GmbH', 'Germany', ?, 3)", C1, OWNER);
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    private void cleanup() {
        for (String t : new String[]{"followup_item", "customer_followup", "customer_inquiry", "sales_order_item", "sales_order"}) {
            jdbc.update("delete from " + t + " where tenant_id = 0");
        }
        jdbc.update("delete from customer where tenant_id = 0");
    }

    private void order(LocalDate salesDate, String brand, String category, int amountCny) {
        long id = nextOrderId++;
        jdbc.update("insert into sales_order (id, tenant_id, so_no, customer_id, owner_id, currency_code, total_amount, total_amount_cny,"
                + " status, progress_code, sales_date, migrated) values (?, 0, ?, ?, ?, 'USD', 1, ?, 1, 'COMPLETED', ?, 1)",
                id, "SO" + id, C1, OWNER, BigDecimal.valueOf(amountCny), salesDate);
        jdbc.update("insert into sales_order_item (tenant_id, so_id, model, quantity, brand, category, amount_cny) values (0, ?, 'M', 1, ?, ?, ?)",
                id, brand, category, BigDecimal.valueOf(amountCny));
    }

    private void sync(LocalDate today, boolean bootstrap) {
        TenantContext.runAs(0, () -> followupSync.resync(C1, today, bootstrap));
    }

    private Map<String, Object> row() {
        List<Map<String, Object>> rows = jdbc.queryForList("select * from followup_item where tenant_id = 0 and board = 5 and customer_id = ?",
                C1);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private static LocalDate date(Object v) {
        return v == null ? null : ((java.sql.Date) v).toLocalDate();
    }

    @Test
    void gradedCustomerGetsAccountItemWithPattern() {
        order(TODAY.minusDays(300), "Siemens", "PLC", 1000);
        order(TODAY.minusDays(210), "Siemens", "PLC", 2000);
        order(TODAY.minusDays(120), "ABB", "变频器", 500);
        sync(TODAY, false);
        Map<String, Object> r = row();
        assertNotNull(r);
        assertEquals(6, ((Number) r.get("target_type")).intValue());
        assertEquals(C1, ((Number) r.get("target_id")).longValue());
        assertEquals("CU300001", r.get("doc_no"));
        assertEquals("G4", r.get("stage_code"));
        assertEquals(11, ((Number) r.get("stage_group")).intValue());
        assertEquals(90, ((Number) r.get("pattern_cycle_days")).intValue());
        assertEquals(TODAY.minusDays(30), date(r.get("pattern_next_date")));
        assertEquals("约每 90 天一次，下次约 " + TODAY.minusDays(30).getMonthValue() + " 月", r.get("pattern_text"));
        assertEquals("Siemens、ABB · PLC、变频器", r.get("pattern_brands"));
        assertEquals(3, ((Number) r.get("recent_order_count")).intValue());
        assertEquals(0, new BigDecimal("3500").compareTo((BigDecimal) r.get("amount_cny")), "近 12 个月成交，本位币");
        assertEquals(TODAY.minusDays(120), date(r.get("last_order_date")));
        assertNull(r.get("dormant_since"), "最近一单 120 天前，不算沉睡");
        // 预计采购日已经过了（在生成当天之前），按 B 级节奏从生成当天起算
        assertTrue(String.valueOf(r.get("account_reason")).startsWith("B 级每"), String.valueOf(r.get("account_reason")));
        assertNotNull(r.get("next_action_date"));
    }

    @Test
    void ungradedCustomerHasNoItem_andLeavesWhenGradeDrops() {
        jdbc.update("update customer set biz_grade = 0 where id = ?", C1);
        sync(TODAY, false);
        assertNull(row(), "未成交客户不进客户经营");
        jdbc.update("update customer set biz_grade = 2 where id = ?", C1);
        order(TODAY.minusDays(30), "Siemens", "PLC", 1000);
        sync(TODAY, false);
        assertNull(row().get("closed_at"));
        jdbc.update("update customer set biz_grade = 0 where id = ?", C1);
        sync(TODAY, false);
        assertNotNull(row().get("closed_at"));
        assertEquals(6, ((Number) row().get("close_reason")).intValue());
        jdbc.update("update customer set biz_grade = 2 where id = ?", C1);
        sync(TODAY, false);
        assertNull(row().get("closed_at"), "重新有等级后重新打开");
    }

    @Test
    void openInquiryMovesToG5_andPausesReminder() {
        order(TODAY.minusDays(60), "Siemens", "PLC", 1000);
        sync(TODAY, false);
        assertEquals("G4", row().get("stage_code"));
        jdbc.update("insert into customer_inquiry (id, tenant_id, inquiry_code, customer_id, owner_id, status, source, inquiry_date,"
                + " quote_deadline) values (99320001, 0, 'INQ99320001', ?, ?, 1, 1, ?, ?)", C1, OWNER, TODAY, TODAY.plusDays(3));
        sync(TODAY, false);
        Map<String, Object> r = row();
        assertEquals("G5", r.get("stage_code"));
        assertEquals(12, ((Number) r.get("stage_group")).intValue());
        assertEquals("复购机会 · 在商机成交跟进中", r.get("next_action"));
        assertNull(r.get("next_action_date"), "商机成交在跟，客户经营不提醒");

        // 询盘不再跟进（删除）→ 回到 G4，按系统节奏恢复提醒
        jdbc.update("update customer_inquiry set deleted_at = now() where id = 99320001");
        sync(TODAY, false);
        r = row();
        assertEquals("G4", r.get("stage_code"));
        assertEquals(1, ((Number) r.get("next_action_source")).intValue());
        assertNotNull(r.get("next_action_date"), "暂停结束，恢复提醒");
    }

    @Test
    void reconcileGeneratesForEveryGradedCustomer() {
        // C1（B 级）之外再加 2 个成交客户、1 个未成交客户、1 个已删除的成交客户
        for (long id = 99300002L; id <= 99300005L; id++) {
            jdbc.update("insert into customer (id, tenant_id, customer_code, name, country, owner_id, biz_grade, deleted_at)"
                    + " values (?, 0, ?, ?, 'Germany', ?, ?, ?)", id, "CU" + id, "C" + id, OWNER, id == 99300004L ? 0 : 2,
                    id == 99300005L ? java.time.LocalDateTime.now() : null);
        }
        TenantContext.runAs(0, () -> reconcileJob.reconcile(0));
        assertEquals(3, jdbc.queryForObject("select count(*) from followup_item where tenant_id = 0 and board = 5 and closed_at is null",
                Integer.class), "成交且未删除的客户各一个");
        TenantContext.runAs(0, () -> reconcileJob.reconcile(0));
        assertEquals(3, jdbc.queryForObject("select count(*) from followup_item where tenant_id = 0 and board = 5", Integer.class),
                "再跑一次不重复生成");
    }

    @Test
    void cancelledAndAfterSalesOrdersAreNotCounted() {
        order(TODAY.minusDays(100), "Siemens", "PLC", 1000);
        jdbc.update("insert into sales_order (id, tenant_id, so_no, customer_id, owner_id, currency_code, total_amount, total_amount_cny,"
                + " status, progress_code, sales_date, migrated) values (99319001, 0, 'SOX1', ?, ?, 'USD', 1, 9000, 2, 'CANCELLED', ?, 1)",
                C1, OWNER, TODAY.minusDays(10));
        jdbc.update("insert into sales_order (id, tenant_id, so_no, customer_id, owner_id, currency_code, total_amount, total_amount_cny,"
                + " status, progress_code, sales_date, migrated, after_sales_id) values (99319002, 0, 'SOX2', ?, ?, 'USD', 1, 8000, 1,"
                + " 'COMPLETED', ?, 1, 5)", C1, OWNER, TODAY.minusDays(5));
        sync(TODAY, false);
        Map<String, Object> r = row();
        assertEquals(1, ((Number) r.get("recent_order_count")).intValue(), "已取消、售后补发不计；迁移订单计入");
        assertEquals(0, new BigDecimal("1000").compareTo((BigDecimal) r.get("amount_cny")));
        assertEquals(TODAY.minusDays(100), date(r.get("last_order_date")));
        assertEquals("数据不足", r.get("pattern_text"));
    }

    @Test
    void dormantThenActivatedByNewOrder() {
        LocalDate last = TODAY.minusDays(250);
        order(last, "Siemens", "PLC", 1000);
        sync(TODAY, false);
        Map<String, Object> r = row();
        assertEquals(TODAY, date(r.get("dormant_since")), "日常同步发现沉睡，从今天算");

        order(TODAY, "Siemens", "PLC", 800);
        sync(TODAY, false);
        assertNull(row().get("dormant_since"));
        List<String> titles = jdbc.queryForList("select title from customer_followup where tenant_id = 0 and customer_id = ? and record_type = 2",
                String.class, C1);
        assertEquals(List.of("沉睡激活：距上一单 250 天后有了新动向"), titles, "天数按激活前的最近一单算");
    }

    @Test
    void bootstrapBackfillsDormantSince() {
        LocalDate last = TODAY.minusDays(400);
        order(last, "Siemens", "PLC", 1000);
        sync(TODAY, true);
        Map<String, Object> r = row();
        LocalDate since = last.plusDays(181);
        assertEquals(since, date(r.get("dormant_since")), "上线补生成按最近一单 + 180 天回推");
        // 每 60 天一个节点：已到达的节点在生成当天之前，顺延一个间隔后仍早于等级节奏，提醒按沉睡激活
        LocalDate node = since.plusDays(ChronoUnit.DAYS.between(since, TODAY) / 60 * 60).plusDays(60);
        assertEquals(node, date(r.get("next_action_date")));
        assertEquals("沉睡 400 天，发同品类现货清单激活", r.get("account_reason"));
        assertEquals("发同品类现货清单或新到货，问近期计划", r.get("next_action"));
    }

    @Test
    void recentInquiryKeepsCustomerAwake() {
        order(TODAY.minusDays(250), "Siemens", "PLC", 1000);
        jdbc.update("insert into customer_inquiry (id, tenant_id, inquiry_code, customer_id, owner_id, status, source, inquiry_date,"
                + " quote_deadline, deleted_at) values (99320002, 0, 'INQ99320002', ?, ?, 1, 1, ?, ?, now())", C1, OWNER, TODAY, TODAY);
        sync(TODAY, false);
        assertNotNull(row().get("dormant_since"), "已删除的询盘不算动向");
        jdbc.update("update customer_inquiry set deleted_at = null where id = 99320002");
        sync(TODAY, false);
        assertNull(row().get("dormant_since"), "近 90 天有询盘，不算沉睡");
    }
}
