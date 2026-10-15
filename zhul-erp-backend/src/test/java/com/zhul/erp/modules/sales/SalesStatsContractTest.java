package com.zhul.erp.modules.sales;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** spec sales/sales-stats 与 inquiry/sourcing-stats */
class SalesStatsContractTest extends SalesContractSupport {

    private static final String STATS = "/api/v1/sales/stats";
    private static final String SOURCING = "/api/v1/inquiry/sourcing-stats";
    private static final int MENU_SALES_STATS = 100113;
    private static final int MENU_SOURCING_STATS = 100114;

    private final LocalDate today = LocalDate.now();
    private final LocalDate earlier = today.minusDays(40);

    @BeforeEach
    void banks() {
        bankAccount("USD");
    }

    @AfterEach
    void cleanOpportunities() {
        jdbc.update("delete from opportunity where tenant_id = 0");
    }

    private void opportunity(long owner) {
        jdbc.update("insert into opportunity (tenant_id, opportunity_code, first_contact_date, owner_id, stage_code) "
                + "values (0, ?, CURDATE(), ?, 'NEW')", "ITOP" + System.nanoTime() % 1_000_000_000L, owner);
    }

    /** 收齐货款并转成订单，返回订单 ID */
    private long paidOrder(long pi, String amount) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("paymentMethod", "ALIBABA_TA");
        body.put("platformOrderNo", "3173948860" + System.nanoTime() % 100000);
        body.put("amount", amount);
        body.put("platformFee", "0");
        body.put("receiptDate", today.toString());
        ok(call(json(post(PI + "/" + pi + "/platform-receipts"), write(body)), admin));
        ok(call(json(post(PI + "/" + pi + "/convert"), "{}"), admin));
        return jdbc.queryForObject("select max(id) from sales_order where tenant_id = 0", Long.class);
    }

    @Test
    void funnelCounts_newOldSplit_rates_pending_views_dataScope() throws Exception {
        opportunity(ADMIN_USER);
        opportunity(ADMIN_USER);
        opportunity(BUYER_LIN);

        // 客户 A：40 天前报价、发 PI、收款转订单（新客户）
        long a = customer("Surya Prima Mandiri CV", "Indonesia");
        long q1 = quotation(a, 2, "USD", null, l("6AV2123-2JB03-0AX0", 1, "1000", "309.19"));
        long pi1 = sentPi(quotationItemIds(q1), null);
        long so = paidOrder(pi1, "309.19");
        jdbc.update("update quotation set sent_at = ? where id = ?", earlier.minusDays(1).atTime(10, 0), q1);
        jdbc.update("update proforma_invoice set sent_at = ? where id = ?", earlier.minusDays(1).atTime(11, 0), pi1);
        jdbc.update("update sales_order set sales_date = ? where id = ?", earlier, so);
        jdbc.update("update customer_inquiry set inquiry_date = ? where id = ?", earlier.minusDays(1), inquiryOf(q1));
        // 客户 B：本月报价、发 PI 未付款（新客户，待付款）
        long b = customer("Pacific Controls", "Australia");
        long q2 = quotation(b, 2, "USD", null, l("6ES7214-1AG40-0XB0", 2, "800", "250"));
        long pi2 = sentPi(quotationItemIds(q2), null);
        // 客户 A 再次询价、报价：已有订单，算老客户
        long q3 = quotation(a, 2, "USD", null, l("6ES7231-4HD32-0XB0", 1, "500", "180"));
        jdbc.update("update customer_inquiry set customer_type = 2 where id = ?", inquiryOf(q3));
        // 已取消的询盘不计
        jdbc.update("insert into customer_inquiry (tenant_id, inquiry_code, customer_id, inquiry_date, quote_deadline, status, owner_id, "
                + "customer_type) values (0, 'ITCANCEL', ?, CURDATE(), CURDATE(), 10, ?, 1)", b, ADMIN_USER);

        String from = today.minusDays(60).toString();
        JsonNode stats = ok(call(get(STATS).param("dateFrom", from).param("dateTo", today.toString()), admin));
        JsonNode t = stats.path("total");
        assertEquals(3, t.path("opportunities").asInt());
        assertEquals(3, t.path("inquiries").path("all").asInt());
        assertEquals(2, t.path("inquiries").path("fresh").asInt());
        assertEquals(1, t.path("inquiries").path("old").asInt());
        assertEquals(3, t.path("quotations").path("all").asInt());
        assertEquals(1, t.path("quotations").path("old").asInt(), "客户 A 第二次报价时已有订单");
        assertEquals(2, t.path("intents").path("all").asInt());
        assertEquals(1, t.path("pending").path("all").asInt(), "已到账的不算待付款");
        money(jdbc.queryForObject("select total_amount_cny from proforma_invoice where id = ?", java.math.BigDecimal.class, pi2)
                .toPlainString(), t.path("pendingAmount").path("all"));
        assertEquals(1, t.path("orders").path("all").asInt());
        assertEquals(1, t.path("orders").path("fresh").asInt());
        assertEquals(1, t.path("customers").path("all").asInt());
        money("100.00", t.path("inquiryRate"));
        money("33.33", t.path("orderRate"));
        money("50.00", t.path("orderRateNew"));
        money("0.00", t.path("orderRateOld"));
        money("33.33", t.path("overallRate"));
        money("0.00", t.path("orderRepeatRate"));
        money("0.00", t.path("customerRepeatRate"));
        JsonNode cost = ok(call(get(SO + "/" + so), admin)).path("cost");
        money(cost.path("revenue").decimalValue().subtract(cost.path("refund").decimalValue()).toPlainString(), t.path("revenue").path("all"));
        money(cost.path("profit").asText(), t.path("profit").path("all"));
        money(cost.path("revenue").decimalValue().subtract(cost.path("refund").decimalValue()).toPlainString(), t.path("avgPrice"));
        assertTrue(t.path("avgPriceOld").isNull() || t.path("avgPriceOld").isMissingNode(), "没有老客户订单，分母为 0");
        assertTrue(stats.path("rows").size() >= 1);

        // 按业务员：两人（管理员与林熙）
        JsonNode byOwner = ok(call(get(STATS).param("dateFrom", from).param("view", "OWNER"), admin)).path("rows");
        assertEquals(2, byOwner.size());
        assertEquals("林熙", byOwner.get(1).path("label").asText());
        assertEquals(1, byOwner.get(1).path("opportunities").asInt());

        // 按客户
        JsonNode customers = ok(call(get(STATS).param("dateFrom", from).param("view", "CUSTOMER"), admin)).path("customers");
        assertEquals(1, customers.size());
        assertEquals("Surya Prima Mandiri CV", customers.get(0).path("customerName").asText());
        assertEquals("Indonesia", customers.get(0).path("country").asText());
        assertEquals(1, customers.get(0).path("orders").asInt());

        // 询价统计：3 张进入询价的询盘单都有价；再加一张全部无货、一张还有待回价
        long noStock = inquiryOf(quotation(b, 2, "USD", null, l("6ES7972-0BA42-0XA0", 1, "100", "30")));
        jdbc.update("update inquiry_item set quote_status = 3 where customer_inquiry_id = ?", noStock);
        long pending = inquiryOf(quotation(b, 2, "USD", null, l("6ES7972-0BB42-0XA0", 1, "100", "30"),
                l("6ES7972-0BB52-0XA0", 1, "100", "30")));
        jdbc.update("update inquiry_item set quote_status = 1 where customer_inquiry_id = ? and line_no = 2", pending);
        jdbc.update("insert into sourcing_task (tenant_id, task_code, customer_inquiry_id, brand, status) values (0, 'ITST1', ?, 'Siemens', 1)",
                pending);
        long task = jdbc.queryForObject("select max(id) from sourcing_task where tenant_id = 0", Long.class);
        jdbc.update("update inquiry_item set sourcing_task_id = ? where customer_inquiry_id = ?", task, pending);
        jdbc.update("insert into sourcing_task_assignee (tenant_id, task_id, assignee_id, active) values (0, ?, ?, 1)", task, BUYER_LIN);
        JsonNode sourcing = ok(call(get(SOURCING).param("dateFrom", from), admin));
        JsonNode st = sourcing.path("total");
        assertEquals(5, st.path("total").asInt());
        assertEquals(3, st.path("completed").asInt());
        assertEquals(1, st.path("unquotable").asInt());
        assertEquals(1, st.path("inProgress").asInt(), "还有待回价的型号");
        money("60.00", st.path("completedRate"));
        money("20.00", st.path("unquotableRate"));
        JsonNode lin = sourcing.path("purchasers").get(0);
        assertEquals("林熙", lin.path("purchaserName").asText());
        assertEquals(2, lin.path("items").asInt());
        assertEquals(1, lin.path("priced").asInt());
        assertEquals(1, lin.path("pending").asInt());
        money("50.00", lin.path("pricedRate"));

        // 只能看自己订单的业务员：林熙的商机不计入；没有菜单的不能访问
        loginWithResources("it_stats_staff", MENU_SALES_STATS);
        String staff = token("it_stats_staff");
        JsonNode mine = ok(call(get(STATS).param("dateFrom", from), staff)).path("total");
        assertEquals(2, mine.path("opportunities").asInt());
        assertEquals(403, perform(get(SOURCING), staff).getStatus());
        loginWithResources("it_sourcing_staff", MENU_SOURCING_STATS);
        assertEquals(403, perform(get(STATS), token("it_sourcing_staff")).getStatus());
    }
}
