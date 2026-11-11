package com.zhul.erp.modules.sales;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** spec sales/sales-order「删除销售订单」 */
class SalesOrderDeleteContractTest extends SalesContractSupport {

    private static final L[] TWO = {l("6ES7214-1AG40-0XB0", 1, "4500", "740.44"), l("LC1D25M7", 2, "120", "18.50")};

    private JsonNode check(long soId, String token) throws Exception {
        return call(get(SO + "/" + soId + "/delete-check"), token);
    }

    private JsonNode delete(long soId, String reason, String token) throws Exception {
        return call(json(post(SO + "/" + soId + "/delete"), write(Map.of("reason", reason))), token);
    }

    private long manualOrder(long customer) throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("customerId", customer);
        m.put("currencyCode", "USD");
        m.put("items", List.of(Map.of("productId", manualProduct("A-1"), "quantity", 2, "unitPrice", "100", "costPrice", "300")));
        return ok(call(json(post(SO), write(m)), admin)).path("id").asLong();
    }

    /** PI 转成、先采购后收款（没有到账）的订单 */
    private long piOrder(long pi) throws Exception {
        return ok(call(json(post(PI + "/" + pi + "/convert"), write(Map.of("payLater", true, "payLaterReason", "老客户"))), admin))
                .path("id").asLong();
    }

    private static List<String> kinds(JsonNode docs) {
        List<String> out = new ArrayList<>();
        docs.forEach(d -> out.add(d.path("kind").asText()));
        return out;
    }

    @Test
    void manualOrder_deletedDirectly_hiddenEverywhere_reasonRequired() throws Exception {
        long so = manualOrder(customer("ACME", "Germany"));
        JsonNode listed = ok(check(so, admin));
        assertEquals("CONFIRM", listed.path("level").asText(), "建单自动生成的采购需求列出来");
        assertEquals(List.of("采购需求"), kinds(listed.path("related")));
        // 采购需求已关闭（如部分取消到 0）时没有后续单据
        jdbc.update("update purchase_requirement set status = 2 where so_id = ?", so);
        JsonNode c = ok(check(so, admin));
        assertEquals("DIRECT", c.path("level").asText());
        assertEquals(0, c.path("blockers").size());
        assertEquals(0, c.path("related").size());

        assertEquals("请填写删除原因", fail(delete(so, " ", admin)).path("message").asText());
        assertTrue(fail(delete(so, "x".repeat(201), admin)).path("message").asText().contains("不能超过 200"));
        String soNo = jdbc.queryForObject("select so_no from sales_order where id = ?", String.class, so);
        String logSql = "select count(*) from sys_log where operation = '删除销售订单' and content like ? and content like ?";
        int logsBefore = jdbc.queryForObject(logSql, Integer.class, "%" + soNo + "%", "%录错客户%");
        ok(delete(so, "录错客户", admin));

        Map<String, Object> row = jdbc.queryForMap("select status, deleted_at, deleted_by, delete_reason from sales_order where id = ?", so);
        assertNotNull(row.get("deleted_at"));
        assertEquals(ADMIN_USER, ((Number) row.get("deleted_by")).longValue());
        assertEquals("录错客户", row.get("delete_reason"));
        JsonNode gone = fail(call(get(SO + "/" + so), admin));
        assertEquals("订单已删除", gone.path("message").asText());
        assertEquals("ORDER_DELETED", gone.path("data").path("errorCode").asText());
        assertEquals("订单已删除", fail(delete(so, "再删一次", admin)).path("message").asText());
        assertEquals(0, ok(call(json(post(SO + "/page"), "{}"), admin)).path("total").asInt());
        assertEquals(0, ok(call(get(SO + "/stats"), admin)).path("inProgressCount").asLong());
        assertEquals(logsBefore + 1, jdbc.queryForObject(logSql, Integer.class, "%" + soNo + "%", "%录错客户%"), "操作日志记录删除原因");
    }

    @Test
    void piOrder_listsLinks_deleteReleasesPiRequirementsAndDraftLines() throws Exception {
        long q = quotation(customer("ACROBOT", "India"), 2, "USD", null, TWO);
        long pi = sentPi(quotationItemIds(q), null);
        long so = piOrder(pi);
        List<Long> reqs = requirementIds(so);
        assertEquals(2, reqs.size());
        long draft = generate(supplier("苏州工控", null), reqs.subList(0, 1), admin);

        JsonNode c = ok(check(so, admin));
        assertEquals("CONFIRM", c.path("level").asText());
        assertEquals(List.of("形式发票", "采购需求", "草稿采购单"), kinds(c.path("related")));
        assertEquals("回到「已发送」，可以再改", c.path("related").get(0).path("note").asText());

        ok(delete(so, "客户改用新 PI 下单", admin));
        assertEquals(2, ok(call(get(PI + "/" + pi), admin)).path("status").asInt(), "PI 回到已发送并解锁");
        assertEquals(0, jdbc.queryForObject("select count(*) from purchase_requirement where so_id = ? and status = 1", Integer.class, so),
                "没下单的采购需求关闭");
        assertEquals(0, jdbc.queryForObject("select count(*) from purchase_order_item where po_id = ? and deleted_at is null", Integer.class,
                draft), "草稿采购单上的行删除");
        assertEquals(2, jdbc.queryForObject("select status from quotation where id = ?", Integer.class, q), "报价单回到已发送");
        // PI 可以再转订单
        assertEquals(1, ok(call(json(post(PI + "/" + pi + "/convert"), write(Map.of("payLater", true, "payLaterReason", "重新下单"))), admin))
                .path("status").asInt());
    }

    @Test
    void receiptsOrderedPoAndFeeOrders_blockDelete() throws Exception {
        long q = quotation(customer("ACROBOT", "India"), 2, "USD", null, TWO);
        long pi = sentPi(quotationItemIds(q), null);
        ok(confirmReceipt(pi, "500", null, false, admin));
        long so = ok(call(post(PI + "/" + pi + "/convert"), admin)).path("id").asLong();
        long po = generate(supplier("苏州工控", null), requirementIds(so), admin);
        priceAndConfirm(po, "100", admin);

        JsonNode c = ok(check(so, admin));
        assertEquals("BLOCKED", c.path("level").asText());
        assertEquals(List.of("到账", "采购订单"), kinds(c.path("blockers")));
        assertEquals("先取消采购订单", c.path("blockers").get(1).path("note").asText());
        JsonNode refused = fail(delete(so, "录错", admin));
        assertEquals("订单有到账、采购订单，不能删除", refused.path("message").asText());
        assertEquals("ORDER_DELETE_BLOCKED", refused.path("data").path("errorCode").asText());
        assertEquals("BLOCKED", refused.path("data").path("detail").path("level").asText());
        assertEquals(null, jdbc.queryForObject("select deleted_at from sales_order where id = ?", Object.class, so));

        long manual = manualOrder(customer("ACME", "Germany"));
        long fee = ok(call(json(post(SO + "/" + manual + "/fee-orders"),
                write(Map.of("fees", List.of(Map.of("feeName", "Freight", "amount", "180"))))), admin)).path("id").asLong();
        assertEquals(List.of("费用订单"), kinds(ok(check(manual, admin)).path("blockers")));
        ok(delete(fee, "不再另收运费", admin));
        JsonNode after = ok(check(manual, admin));
        assertEquals("CONFIRM", after.path("level").asText(), "费用订单删除后原订单可以删");
        assertEquals(0, after.path("blockers").size());
    }

    @Test
    void cancelledOrder_deletable_piUnchanged() throws Exception {
        long q = quotation(customer("ACROBOT", "India"), 2, "USD", null, TWO);
        long pi = sentPi(quotationItemIds(q), null);
        long so = piOrder(pi);
        ok(call(json(post(SO + "/" + so + "/cancel"), write(Map.of("reason", "客户取消"))), admin));

        JsonNode c = ok(check(so, admin));
        assertEquals("CONFIRM", c.path("level").asText());
        assertEquals(List.of("形式发票"), kinds(c.path("related")));
        assertEquals("取消时已处理，不变", c.path("related").get(0).path("note").asText());
        ok(delete(so, "清理已取消订单", admin));
        assertEquals(2, ok(call(get(PI + "/" + pi), admin)).path("status").asInt(), "PI 保持取消时的已发送");
    }

    @Test
    void completedMigratedAndNoPermission_refused() throws Exception {
        long so = manualOrder(customer("ACME", "Germany"));
        jdbc.update("update sales_order set progress_code = 'COMPLETED' where id = ?", so);
        assertEquals("已完成的订单不能删除", fail(check(so, admin)).path("message").asText());
        assertEquals("已完成的订单不能删除", fail(delete(so, "录错", admin)).path("message").asText());

        jdbc.update("update sales_order set progress_code = 'PENDING_RECEIPT', migrated = 1 where id = ?", so);
        assertTrue(fail(delete(so, "录错", admin)).path("message").asText().contains("迁移"));

        jdbc.update("update sales_order set migrated = 0 where id = ?", so);
        loginWithResources("it_so_staff", MENU_SO);
        String staff = token("it_so_staff");
        assertEquals(403, perform(get(SO + "/" + so + "/delete-check"), staff).getStatus());
        assertEquals(403, perform(json(post(SO + "/" + so + "/delete"), write(Map.of("reason", "录错"))), staff).getStatus());
        assertEquals(null, jdbc.queryForObject("select deleted_at from sales_order where id = ?", Object.class, so));
    }

    @Test
    void outboundAfterSalesRefundsExpensesAndHolds_blockDelete() throws Exception {
        long so = manualOrder(customer("ACME", "Germany"));
        long item = jdbc.queryForObject("select id from sales_order_item where so_id = ?", Long.class, so);
        long cust = jdbc.queryForObject("select customer_id from sales_order where id = ?", Long.class, so);
        jdbc.update("insert into outbound_order (tenant_id, ob_no, so_id, status) values (0, 'OBIT001', ?, 1)", so);
        jdbc.update("insert into outbound_order (tenant_id, ob_no, so_id, status) values (0, 'OBIT002', ?, 4)", so);
        jdbc.update("insert into after_sales (tenant_id, as_no, so_id, customer_id, status) values (0, 'ASIT001', ?, ?, 1)", so, cust);
        long as = jdbc.queryForObject("select id from after_sales where as_no = 'ASIT001'", Long.class);
        jdbc.update("insert into after_sales (tenant_id, as_no, so_id, customer_id, status) values (0, 'ASIT002', ?, ?, 4)", so, cust);
        jdbc.update("insert into sales_return (tenant_id, return_no, after_sales_id, so_id, status) values (0, 'RTIT001', ?, ?, 1)", as, so);
        jdbc.update("insert into fin_refund_request (tenant_id, request_no, so_id, currency_code, amount, method, status) "
                + "values (0, 'RQIT001', ?, 'USD', 10, 2, 1)", so);
        jdbc.update("insert into fin_refund_request (tenant_id, request_no, so_id, currency_code, amount, method, status) "
                + "values (0, 'RQIT002', ?, 'USD', 10, 2, 5)", so);
        jdbc.update("insert into fin_customer_refund (tenant_id, refund_no, so_id, refund_date, currency_code, amount, exchange_rate, amount_cny, status) "
                + "values (0, 'RFIT001', ?, CURDATE(), 'USD', 10, 7.15, 71.5, 1)", so);
        jdbc.update("insert into fin_expense (tenant_id, expense_no, occur_date, status) values (0, 'FEIT001', CURDATE(), 1)");
        long fe = jdbc.queryForObject("select id from fin_expense where expense_no = 'FEIT001'", Long.class);
        jdbc.update("insert into fin_expense_allocation (tenant_id, expense_id, so_id, so_item_id, amount) values (0, ?, ?, ?, 5)", fe, so, item);
        jdbc.update("insert into stock_hold (tenant_id, source_type, so_id, so_item_id, model, quantity, status) values (0, 2, ?, ?, 'A-1', 1, 1)",
                so, item);

        JsonNode c = ok(check(so, admin));
        assertEquals("BLOCKED", c.path("level").asText());
        assertEquals(List.of("出库单", "售后单", "退货单", "退款申请", "退款", "费用分摊", "暂存货物"), kinds(c.path("blockers")),
                "已撤回的出库单、已撤销的售后单、已撤回的退款申请不挡");
        assertEquals("先撤回出库", c.path("blockers").get(0).path("note").asText());
        assertEquals("/sales/after-sales/" + as, c.path("blockers").get(1).path("path").asText());

        // 售后补发的订单
        long replacement = manualOrder(cust);
        jdbc.update("update sales_order set source = 3, after_sales_id = ? where id = ?", as, replacement);
        assertEquals(List.of("售后补发"), kinds(ok(check(replacement, admin)).path("blockers")));
    }
}
