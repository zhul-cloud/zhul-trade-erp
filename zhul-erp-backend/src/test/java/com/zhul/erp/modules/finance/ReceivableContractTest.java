package com.zhul.erp.modules.finance;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhul.erp.modules.sales.SalesContractSupport;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** spec finance/accounts-receivable：有效销售订单里没收齐的款，按客户汇总、逾期在前 */
class ReceivableContractTest extends SalesContractSupport {

    private static final String API = "/api/v1/finance/receivables";

    /** source 2 = 手动；piId 不为空时收款取 PI */
    private void order(String soNo, long customer, String currency, String rate, String total, String received,
                       Integer receiptStatus, LocalDate due, Long piId, int status) {
        jdbc.update("insert into sales_order (tenant_id, so_no, source, sales_date, customer_id, owner_id, currency_code, exchange_rate, "
                        + "total_amount, received_amount, fee_diff_amount, receipt_status, payment_due_date, pi_id, status) "
                        + "values (0, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?, ?)",
                soNo, piId == null ? 2 : 1, LocalDate.now().minusDays(30), customer, ADMIN_USER, currency, rate, total, received,
                receiptStatus, due, piId == null ? 0L : piId, status);
    }

    private static void eq(String expected, JsonNode n) {
        assertEquals(0, new BigDecimal(expected).compareTo(n.decimalValue()), "期望 " + expected + "，实际 " + n);
    }

    @Test
    void openOrdersOnly_piReceiptsUsed_overdueFirst_buckets() throws Exception {
        long acme = customer("IT AR Acme", "Australia");
        long bolt = customer("IT AR Bolt", "India");
        LocalDate today = LocalDate.now();
        jdbc.update("insert into proforma_invoice (tenant_id, pi_no, customer_id, owner_id, currency_code, status, receipt_status, received_amount, fee_diff_amount) "
                + "values (0, 'ITAR-PI-1', ?, ?, 'USD', 4, 3, 300, 0)", acme, ADMIN_USER);
        long pi = jdbc.queryForObject("select max(id) from proforma_invoice", Long.class);
        order("ITAR-SO-1", acme, "USD", "7.2", "1000", "0", 1, today.minusDays(12), pi, 1);          // 未收 700（取 PI 的已收 300），逾期 12 天
        order("ITAR-SO-2", acme, "USD", "7.2", "500", "0", 1, today.plusDays(3), null, 1);          // 未收 500，3 天后到期
        order("ITAR-SO-3", bolt, "CNY", "1", "800", "200", 3, null, null, 1);                       // 未收 600，没约定
        order("ITAR-SO-4", bolt, "USD", "7.2", "900", "900", 4, today.minusDays(5), null, 1);       // 已收齐，不出现
        order("ITAR-SO-5", bolt, "USD", "7.2", "900", "0", 1, today.minusDays(5), null, 3);         // 已取消，不出现

        JsonNode r = ok(call(get(API), admin));
        JsonNode rows = r.path("rows");
        assertEquals(3, rows.size(), r.toString());
        assertEquals("ITAR-SO-1", rows.get(0).path("soNo").asText(), "逾期的在前");
        assertEquals("ITAR-PI-1", rows.get(0).path("piNo").asText());
        eq("300", rows.get(0).path("receivedAmount"));
        eq("700", rows.get(0).path("remainingAmount"));
        eq("5040", rows.get(0).path("remainingCny"));
        assertEquals(12, rows.get(0).path("overdueDays").asInt());
        assertEquals("Australia", rows.get(0).path("customerCountry").asText());
        assertEquals(3, rows.get(1).path("dueInDays").asInt());
        assertEquals("ITAR-SO-3", rows.get(2).path("soNo").asText(), "没约定付款日的在最后");
        assertTrue(rows.get(2).path("dueDate").isNull() || rows.get(2).path("dueDate").isMissingNode());

        JsonNode s = r.path("stats");
        eq("9240", s.path("totalCny"));
        assertEquals(2, s.path("customers").asInt());
        assertEquals(3, s.path("orders").asInt());
        eq("5040", s.path("overdueCny"));
        assertEquals(1, s.path("overdueOrders").asInt());
        eq("3600", s.path("weekCny"));
        assertEquals(1, s.path("weekOrders").asInt());
    }
}
