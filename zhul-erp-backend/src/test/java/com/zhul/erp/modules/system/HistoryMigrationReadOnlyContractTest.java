package com.zhul.erp.modules.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhul.erp.modules.sales.SalesContractSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** spec system/history-migration「迁移数据只读」：迁移生成的单据拒绝修改、作废、取消；迁移的商机可以照常跟进 */
class HistoryMigrationReadOnlyContractTest extends SalesContractSupport {

    private static final String REASON = "{\"reason\":\"测试\"}";

    @AfterEach
    void cleanOpportunities() {
        jdbc.update("delete from opportunity_stage_log where tenant_id = 0");
        jdbc.update("delete from opportunity where tenant_id = 0 and opportunity_code like 'ITHM%'");
    }

    private static void readOnly(JsonNode res) {
        fail(res);
        assertEquals("MIGRATED_READ_ONLY", res.path("data").path("errorCode").asText(), res.toString());
        assertEquals("历史迁移数据不能修改", res.path("message").asText());
    }

    private long lastId(String table) {
        return jdbc.queryForObject("select max(id) from " + table + " where tenant_id = 0", Long.class);
    }

    private long manualOrder(long customer, int migrated) {
        jdbc.update("insert into sales_order (tenant_id, so_no, source, sales_date, customer_id, owner_id, currency_code, exchange_rate, "
                + "status, progress_code, migrated) values (0, ?, 2, '2025-05-12', ?, ?, 'USD', 7.100000, 1, 'ORDERED', ?)",
                "ITHM" + System.nanoTime() % 1_000_000_000L, customer, ADMIN_USER, migrated);
        return lastId("sales_order");
    }

    @Test
    void migratedOrderReceiptPurchaseExpenseIncomeSettlement_rejectEdits() throws Exception {
        long customer = customer("HM Customer", "Mexico");

        long so = manualOrder(customer, 1);
        readOnly(call(json(post(SO + "/" + so + "/cancel"), REASON), admin));

        // 到账是迁移的，订单不是：作废到账也被拒绝
        long normal = manualOrder(customer, 0);
        jdbc.update("insert into payment_receipt (tenant_id, so_id, kind, currency_code, amount, status, migrated) values (0, ?, 2, 'USD', 430, 1, 1)", normal);
        readOnly(call(json(post(SO + "/" + normal + "/receipts/" + lastId("payment_receipt") + "/void"), REASON), admin));

        jdbc.update("insert into purchase_order (tenant_id, po_no, shop_name, channel, purchaser_id, status, order_date, currency_code, exchange_rate, migrated) "
                + "values (0, 'ITHMPO1', '某店', 1, ?, 2, '2025-05-12', 'CNY', 1, 1)", ADMIN_USER);
        readOnly(call(json(post(PO + "/" + lastId("purchase_order") + "/cancel"), REASON), admin));

        jdbc.update("insert into fin_expense (tenant_id, expense_no, name, category, amortize_months, occur_date, currency_code, amount, exchange_rate, amount_cny, status, migrated) "
                + "values (0, 'ITHMFE1', '2025-07 房租', 'RENT', 1, '2025-07-31', 'CNY', 1100, 1, 1100, 1, 1)");
        readOnly(call(json(post("/api/v1/finance/expenses/" + lastId("fin_expense") + "/void"), REASON), admin));

        jdbc.update("insert into fin_other_income (tenant_id, income_no, income_date, category, amount, status, migrated) values (0, 'ITHMOI1', '2025-07-31', 'OTHER', 100, 1, 1)");
        readOnly(call(json(post("/api/v1/finance/other-incomes/" + lastId("fin_other_income") + "/void"), REASON), admin));

        // 结汇单沿用原有的迁移标记与提示
        jdbc.update("insert into fin_fx_settlement (tenant_id, settle_no, currency_code, settle_date, amount, amount_cny, exchange_rate, migrated, status) "
                + "values (0, 'ITHMJH1', 'USD', '2025-05-12', 417.10, 2790.40, 6.690000, 1, 1)");
        JsonNode fx = fail(call(json(post("/api/v1/finance/fx-settlements/" + lastId("fin_fx_settlement") + "/void"), REASON), admin));
        assertEquals("迁移生成的结汇单不能作废", fx.path("message").asText());

        assertEquals(1, jdbc.queryForObject("select status from sales_order where id = ?", Integer.class, so));
    }

    @Test
    void migratedOpportunity_canBeFollowedUp_andBecomesNormal() throws Exception {
        long customer = customer("HM Opp Customer", "Mexico");
        jdbc.update("insert into opportunity (tenant_id, opportunity_code, customer_id, first_contact_date, owner_id, stage_code, migrated) "
                + "values (0, 'ITHMOPP1', ?, '2025-09-10', ?, 'S4', 1)", customer, ADMIN_USER);
        long active = lastId("opportunity");
        ok(call(json(post("/api/v1/crm/opportunities/" + active + "/stage"), "{\"toStage\":\"S5\"}"), admin));
        assertEquals("S5", jdbc.queryForObject("select stage_code from opportunity where id = ?", String.class, active));
        assertEquals(0, jdbc.queryForObject("select migrated from opportunity where id = ?", Integer.class, active));

        long customer2 = customer("HM Lost Customer", "Peru");
        jdbc.update("insert into opportunity (tenant_id, opportunity_code, customer_id, first_contact_date, owner_id, stage_code, reopen_stage_code, close_reason, migrated) "
                + "values (0, 'ITHMOPP2', ?, '2025-09-10', ?, 'LOST', 'S4', 11, 1)", customer2, ADMIN_USER);
        long lost = lastId("opportunity");
        ok(call(post("/api/v1/crm/opportunities/" + lost + "/reopen"), admin));
        assertEquals("S4", jdbc.queryForObject("select stage_code from opportunity where id = ?", String.class, lost));
        assertEquals(0, jdbc.queryForObject("select migrated from opportunity where id = ?", Integer.class, lost));
    }
}
