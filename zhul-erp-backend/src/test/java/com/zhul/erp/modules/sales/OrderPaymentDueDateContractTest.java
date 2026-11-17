package com.zhul.erp.modules.sales;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.workbench.dto.WidgetData;
import com.zhul.erp.modules.workbench.widget.WorkbenchWidget;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** spec sales/sales-order「订单约定付款日」「订单逾期未收款标记」、system/workbench「老板看板」的过期未收款 */
class OrderPaymentDueDateContractTest extends SalesContractSupport {

    private static final Pattern USD = Pattern.compile("USD ([0-9,]+\\.[0-9]{2})");

    @Autowired @Qualifier("cashWidget") private WorkbenchWidget cashWidget;

    private final LocalDate today = LocalDate.now();

    /** 手动订单 USD 200.00（2 × 100） */
    private JsonNode manualOrder(LocalDate salesDate, LocalDate due) throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("customerId", customer("Due Buyer", "US"));
        m.put("salesDate", salesDate.toString());
        if (due != null) {
            m.put("paymentDueDate", due.toString());
        }
        m.put("items", List.of(Map.of("productId", manualProduct("D-1"), "quantity", 2, "unitPrice", "100", "costPrice", "300")));
        return call(json(post(SO), write(m)), admin);
    }

    /** 销售订单列表默认按销售日期倒序（2026-11-17 用户确认，列表页统一规范的例外）：后建但日期早的排在后面 */
    @org.junit.jupiter.api.Test
    void listSortedBySalesDateDesc() throws Exception {
        long later = ok(manualOrder(today.minusDays(1), null)).path("id").asLong();
        long earlier = ok(manualOrder(today.minusDays(10), null)).path("id").asLong();
        long sameDayNewer = ok(manualOrder(today.minusDays(1), null)).path("id").asLong();
        java.util.List<Long> ids = new java.util.ArrayList<>();
        ok(call(json(post(SO + "/page"), "{\"pageSize\":100}"), admin)).path("records").forEach(r -> ids.add(r.path("id").asLong()));
        assertEquals(java.util.List.of(sameDayNewer, later, earlier),
                ids.stream().filter(java.util.List.of(later, earlier, sameDayNewer)::contains).toList(), ids.toString());
    }

    private JsonNode setDue(long id, LocalDate due) throws Exception {
        Map<String, Object> b = new HashMap<>();
        b.put("paymentDueDate", due == null ? null : due.toString());
        return call(json(post(SO + "/" + id + "/payment-due-date"), write(b)), admin);
    }

    private JsonNode detail(long id) throws Exception {
        return ok(call(get(SO + "/" + id), admin));
    }

    private void receive(long id, String amount) throws Exception {
        Map<String, Object> r = new HashMap<>();
        r.put("amount", amount);
        r.put("receiptDate", today.toString());
        r.put("bankAccountId", jdbc.queryForObject("select min(id) from tenant_bank_account where tenant_id = 0", Integer.class));
        ok(call(json(post(SO + "/" + id + "/receipts"), write(r)), admin));
    }

    @Test
    void createWithDueDate_validatedAgainstSalesDate() throws Exception {
        assertEquals("约定付款日不能早于销售日期",
                fail(manualOrder(today.minusDays(5), today.minusDays(6))).path("message").asText());

        JsonNode o = ok(manualOrder(today.minusDays(5), today.plusDays(20)));
        assertEquals(today.plusDays(20).toString(), o.path("paymentDueDate").asText());
        assertTrue(absent(o.path("overdueDays")), "还没到约定付款日");

        JsonNode none = ok(manualOrder(today, null));
        assertTrue(absent(none.path("paymentDueDate")));
        assertTrue(absent(none.path("overdueDays")));
    }

    @Test
    void feeOrderWithDueDate() throws Exception {
        bankAccount("USD");
        long original = ok(manualOrder(today, null)).path("id").asLong();
        Map<String, Object> bad = new LinkedHashMap<>();
        bad.put("salesDate", today.toString());
        bad.put("paymentDueDate", today.minusDays(1).toString());
        bad.put("fees", List.of(Map.of("feeName", "Freight", "amount", "80")));
        assertEquals("约定付款日不能早于销售日期",
                fail(call(json(post(SO + "/" + original + "/fee-orders"), write(bad)), admin)).path("message").asText());
        bad.put("paymentDueDate", today.plusDays(10).toString());
        JsonNode fee = ok(call(json(post(SO + "/" + original + "/fee-orders"), write(bad)), admin));
        assertEquals(today.plusDays(10).toString(), fee.path("paymentDueDate").asText());
    }

    @Test
    void updateAndClear_writesLog_andGuardsSalesDate() throws Exception {
        JsonNode created = ok(manualOrder(today.minusDays(10), null));
        long id = created.path("id").asLong();
        String soNo = created.path("soNo").asText();

        assertEquals("约定付款日不能早于销售日期", fail(setDue(id, today.minusDays(11))).path("message").asText());
        long logBefore = jdbc.queryForObject("select coalesce(max(id), 0) from sys_log", Long.class);
        JsonNode set = ok(setDue(id, today.minusDays(3)));
        assertEquals(today.minusDays(3).toString(), set.path("paymentDueDate").asText());
        assertEquals(1, jdbc.queryForObject("select count(*) from sys_log where id > ? and operation = '修改约定付款日'"
                + " and content like ? and content like '%未设置%' and content like ?",
                Integer.class, logBefore, "%" + soNo + "%", "%" + today.minusDays(3) + "%"));

        // 销售日期不能改到约定付款日之后
        assertEquals("销售日期不能晚于约定付款日", fail(call(json(post(SO + "/" + id + "/sales-date"),
                write(Map.of("salesDate", today.minusDays(1).toString()))), admin)).path("message").asText());

        JsonNode cleared = ok(setDue(id, null));
        assertTrue(absent(cleared.path("paymentDueDate")));
        assertTrue(absent(cleared.path("overdueDays")));
        assertNull(jdbc.queryForObject("select payment_due_date from sales_order where id = ?", LocalDate.class, id));
    }

    @Test
    void piOrder_cannotSetDueDate() throws Exception {
        long id = ok(manualOrder(today, null)).path("id").asLong();
        jdbc.update("update sales_order set source = 1 where id = ?", id);
        assertEquals("这张订单的收款不记在订单上，不能设置约定付款日", fail(setDue(id, today.plusDays(3))).path("message").asText());
    }

    @Test
    void overdueDays_dueDayNotOverdue_paidNotOverdue() throws Exception {
        bankAccount("USD");
        long id = ok(manualOrder(today.minusDays(10), today.minusDays(4))).path("id").asLong();
        assertEquals(4, detail(id).path("overdueDays").asLong());

        receive(id, "50");
        assertEquals(4, detail(id).path("overdueDays").asLong(), "部分到账仍逾期");

        ok(setDue(id, today));
        assertTrue(absent(detail(id).path("overdueDays")), "约定付款日当天不算逾期");

        ok(setDue(id, today.minusDays(1)));
        receive(id, "150");
        assertTrue(absent(detail(id).path("overdueDays")), "付清后不再逾期");
    }

    @Test
    void bossOverdue_countsRemainingOfOverdueOrders() throws Exception {
        bankAccount("USD");
        BigDecimal before = overdueUsd();

        long overdue = ok(manualOrder(today.minusDays(5), today.minusDays(1))).path("id").asLong();
        receive(overdue, "50");
        ok(manualOrder(today.minusDays(5), null));
        ok(manualOrder(today.minusDays(5), today));

        // 只有过了约定付款日的那张，按剩余未收 USD 150.00 计入
        assertEquals(0, new BigDecimal("150.00").compareTo(overdueUsd().subtract(before)),
                "过期未收款应只增加逾期订单的剩余未收");
    }

    /** 接口不返回值为空的字段 */
    private static boolean absent(JsonNode n) {
        return n.isMissingNode() || n.isNull();
    }

    /** 老板看板资金卡「其中过期未收款」里的 USD 金额（没有时为 0） */
    private BigDecimal overdueUsd() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("it_sales_admin", null, List.of()));
        TenantContext.setTenantId(0);
        try {
            WidgetData d = (WidgetData) cashWidget.load();
            String hint = d.getBlocks().get(0).getMetrics().stream().filter(m -> "应收未收".equals(m.getLabel()))
                    .findFirst().orElseThrow().getHint();
            Matcher m = USD.matcher(hint);
            return m.find() ? new BigDecimal(m.group(1).replace(",", "")) : BigDecimal.ZERO;
        } finally {
            TenantContext.clear();
            SecurityContextHolder.clearContext();
        }
    }
}
