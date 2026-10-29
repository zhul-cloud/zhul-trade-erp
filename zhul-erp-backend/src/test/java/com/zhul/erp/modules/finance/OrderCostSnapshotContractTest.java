package com.zhul.erp.modules.finance;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhul.erp.modules.sales.SalesContractSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** spec finance/order-cost「迁移订单按历史核算快照」：订单成本、利润统计与经营统计对迁移订单取快照值 */
class OrderCostSnapshotContractTest extends SalesContractSupport {

    @AfterEach
    void cleanSnapshots() {
        jdbc.update("delete from sales_order_cost_snapshot where tenant_id = 0");
    }

    private long order(long customer, String currency, String totalCny, String... lineAmounts) {
        jdbc.update("insert into sales_order (tenant_id, so_no, source, sales_date, customer_id, owner_id, currency_code, exchange_rate, "
                + "total_amount_cny, status, progress_code, migrated) values (0, ?, 2, CURDATE(), ?, ?, ?, 6.690000, ?, 1, 'COMPLETED', 1)",
                "ITSNAP" + System.nanoTime() % 1_000_000_000L, customer, ADMIN_USER, currency, totalCny);
        long so = jdbc.queryForObject("select max(id) from sales_order where tenant_id = 0", Long.class);
        int no = 1;
        for (String amount : lineAmounts) {
            jdbc.update("insert into sales_order_item (tenant_id, so_id, line_no, model, quantity, unit_price, amount) values (0, ?, ?, ?, 1, ?, ?)",
                    so, no, "MODEL-" + no, amount, amount);
            no++;
        }
        return so;
    }

    private void snapshot(long so, String revenue, String cost, String profit, int settled) {
        jdbc.update("insert into sales_order_cost_snapshot (tenant_id, so_id, revenue, cost, gross_profit, service_fee, settled, accounting_month, source_codes) "
                + "values (0, ?, ?, ?, ?, 86.30, ?, '2026-10', 'HS20261023667')", so, revenue, cost, profit, settled);
    }

    private JsonNode cost(long so) throws Exception {
        return ok(call(get(SO + "/" + so), admin)).path("cost");
    }

    @Test
    void snapshotCost_lines_refund_zero_and_reports() throws Exception {
        long customer = customer("Diego Navarro", "Mexico");

        // 规格场景「迁移订单取快照」：没有入库记录，也不提示还没入库
        long so1 = order(customer, "USD", "2876.70", "430.00");
        snapshot(so1, "2876.70", "1807.75", "1068.95", 0);
        JsonNode c1 = cost(so1);
        assertTrue(c1.path("historySnapshot").asBoolean());
        money("2876.70", c1.path("revenue"));
        money("1807.75", c1.path("cost"));
        money("1068.95", c1.path("profit"));
        money("37.16", c1.path("profitRate"));
        // 切换日之前的单据一律完结：快照里没结算的也算已结算
        assertTrue(c1.path("settled").asBoolean());
        assertEquals(0, c1.path("pending").size());
        assertEquals("HS20261023667", c1.path("snapshot").path("sourceCodes").asText());

        // 规格场景「迁移订单的行分摊」
        long so2 = order(customer, "USD", "41145.48", "1776.31", "4401.69");
        snapshot(so2, "41145.48", "34842.00", "6303.48", 1);
        JsonNode lines = cost(so2).path("lines");
        money("11830.22", lines.get(0).path("revenue"));
        money("29315.26", lines.get(1).path("revenue"));
        money("10017.84", lines.get(0).path("cost"));
        money("24824.16", lines.get(1).path("cost"));
        assertTrue(cost(so2).path("settled").asBoolean());

        // 切换后对迁移订单新建的退款单照常扣减
        jdbc.update("insert into fin_customer_refund (tenant_id, refund_no, so_id, refund_date, reason, currency_code, amount, exchange_rate, amount_cny, status) "
                + "values (0, 'ITTK1', ?, CURDATE(), 'QUALITY', 'USD', 14.95, 6.689000, 100.00, 1)", so1);
        JsonNode refunded = cost(so1);
        money("100.00", refunded.path("refund"));
        money("968.95", refunded.path("profit"));
        money("34.90", refunded.path("profitRate"));

        // 零收入的人民币订单：毛利率为空
        long so3 = order(customer, "CNY", "0", "0");
        snapshot(so3, "0", "50.00", "-50.00", 1);
        JsonNode c3 = cost(so3);
        money("-50.00", c3.path("profit"));
        assertTrue(c3.path("profitRate").isNull() || c3.path("profitRate").isMissingNode());

        // 利润统计与经营统计取快照值
        JsonNode stats = ok(call(get("/api/v1/finance/profit-stats"), admin));
        assertEquals(3, stats.path("summary").path("orderCount").asInt());
        JsonNode report = ok(call(get("/api/v1/finance/operating-report"), admin));
        JsonNode month = report.path("rows").get(report.path("rows").size() - 1);
        money("44022.18", month.path("revenue"));
        money("36699.75", month.path("cost"));
    }
}
