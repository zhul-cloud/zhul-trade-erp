package com.zhul.erp.modules.supply;

import com.zhul.erp.modules.inquiry.sourcing.event.SourcingQuoteEvent;
import com.zhul.erp.modules.supply.support.QuoteLinkage;
import com.zhul.erp.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 询价联动在线程池里执行，线程没有租户上下文：按事件里的租户写处理记录、改覆盖状态（tasks 4.4a）。
 * 在另一个线程里调用线程池入口，等同于真实线程池；同时覆盖回价生效 → 撤回 → 重复触发不重复写。
 */
class QuoteLinkageTenantContractTest extends IntegrationTestBase {

    private static final int TENANT = 99000095;
    private static final long BRAND = 99000095L;

    @Autowired private QuoteLinkage linkage;

    private long quoteId;
    private long demandId;

    @BeforeEach
    void data() {
        cleanup();
        jdbc.update("insert into sourcing_task (tenant_id, task_code) values (?, 'IT-SC-T1')", TENANT);
        long taskId = jdbc.queryForObject("select id from sourcing_task where tenant_id = ?", Long.class, TENANT);
        jdbc.update("insert into sourcing_quote (tenant_id, task_id, brand, model, model_key, currency_code, unit_price,"
                + " no_stock, status, quoted_at) values (?, ?, 'ITBRAND', 'IT-M1', 'ITM1', 'CNY', 12.50, 0, 2, now())",
                TENANT, taskId);
        quoteId = jdbc.queryForObject("select id from sourcing_quote where tenant_id = ?", Long.class, TENANT);
        jdbc.update("insert into inquiry_item (tenant_id, brand, brand_id, original_model, model_key, quantity)"
                + " values (?, 'ITBRAND', ?, 'IT-M1', 'ITM1', 1)", TENANT, BRAND);
        long itemId = jdbc.queryForObject("select id from inquiry_item where tenant_id = ?", Long.class, TENANT);
        jdbc.update("update sourcing_quote set inquiry_item_id = ? where id = ?", itemId, quoteId);
        jdbc.update("insert into sc_demand (tenant_id, brand_id, model_key, model, scope_key, status, coverage_status,"
                + " suggested_coverage) values (?, ?, 'ITM1', 'IT-M1', 'm:ITM1', 2, 1, 1)", TENANT, BRAND);
        demandId = jdbc.queryForObject("select id from sc_demand where tenant_id = ?", Long.class, TENANT);
    }

    @AfterEach
    void cleanup() {
        for (String t : List.of("sc_dev_candidate", "sc_gap_project", "supplier", "sc_followup_log", "sc_demand", "sc_demand_signal", "sourcing_quote", "sourcing_task",
                "inquiry_item")) {
            jdbc.update("delete from " + t + " where tenant_id = ?", TENANT);
        }
        jdbc.update("delete from document_log where tenant_id = ? and doc_type = 'SC'", TENANT);
    }

    @Test
    void quoteLinkageRunsUnderEventTenant() throws Exception {
        inOtherThread(false);
        assertEquals(4, coverage(), "回价生效 → 可直接报价");
        assertEquals(1, logs(1), "登记一条回价处理记录");
        assertTrue(jdbc.queryForObject("select content from sc_followup_log where tenant_id = ? and source_type = 1",
                String.class, TENANT).startsWith("询价任务 IT-SC-T1 回价："));

        inOtherThread(false);
        assertEquals(1, logs(1), "重复触发不重复登记");

        jdbc.update("update sourcing_quote set status = 1 where id = ?", quoteId);
        inOtherThread(true);
        assertEquals(1, logs(3), "登记一条撤回处理记录");
        assertEquals(1, coverage(), "没有其他有效证据，改回建议状态");
    }

    /** 第二期：同一个线程池回调里，按事件租户把已关联供应商库的候选标为合格（tasks 4.4） */
    @Test
    void candidateQualifiedUnderEventTenant() throws Exception {
        jdbc.update("insert into supplier (tenant_id, supplier_code, name, status) values (?, 'IT-SC-SUP', 'IT候选甲', 1)", TENANT);
        long supplier = jdbc.queryForObject("select id from supplier where tenant_id = ?", Long.class, TENANT);
        jdbc.update("update sourcing_quote set supplier_id = ? where id = ?", supplier, quoteId);
        jdbc.update("insert into sc_gap_project (tenant_id, name, brand_id, category_id, goal, owner_id, due_date, supplier_target,"
                + " status, active_scope) values (?, 'IT开发', ?, 0, 'x', 0, curdate(), 1, 1, ?)", TENANT, BRAND, BRAND + ":0");
        long project = jdbc.queryForObject("select id from sc_gap_project where tenant_id = ?", Long.class, TENANT);
        jdbc.update("insert into sc_dev_candidate (tenant_id, project_id, supplier_id, link_key, name, stage) values (?, ?, ?, ?,"
                + " 'IT候选甲', 3)", TENANT, project, supplier, project + ":" + supplier);
        inOtherThread(false);
        assertEquals(4, jdbc.queryForObject("select stage from sc_dev_candidate where tenant_id = ?", Integer.class, TENANT),
                "回价后候选自动合格");
        assertEquals(1, jdbc.queryForObject("select count(*) from sc_followup_log where tenant_id = ? and object_type = 2",
                Integer.class, TENANT), "写一条候选的系统处理记录");
    }

    private void inOtherThread(boolean withdrawn) throws InterruptedException {
        SourcingQuoteEvent e = new SourcingQuoteEvent(TENANT, 0L, List.of(quoteId), withdrawn);
        Thread t = new Thread(() -> linkage.handleAsync(e));
        t.start();
        t.join();
    }

    private int coverage() {
        return jdbc.queryForObject("select coverage_status from sc_demand where id = ?", Integer.class, demandId);
    }

    private int logs(int sourceType) {
        return jdbc.queryForObject("select count(*) from sc_followup_log where tenant_id = ? and source_type = ?",
                Integer.class, TENANT, sourceType);
    }
}
