package com.zhul.erp.modules.sales;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * spec sales/after-sales「发起补发」「补发未出运不能结案」、sales/sales-order「售后补发订单」、
 * finance/order-cost「售后补发成本计入原订单」、sales/sales-stats、finance/profit-stats、finance/operating-report 不计补发。
 */
class AfterSalesReplacementContractTest extends SalesContractSupport {

    private static final String AS = "/api/v1/sales/after-sales";

    /** 订单 A-1 × 10（售价 USD 50、采购成本价 ¥200），已收齐并转订单 */
    private long paidOrder() throws Exception {
        long c = customer("Siam Automation", "Thailand");
        long pi = sentPi(quotationItemIds(quotation(c, 2, "USD", null, l("A-1", 10, "200", "50"))), null);
        ok(confirmReceipt(pi, "500", null, false, admin));
        ok(call(json(post(PI + "/" + pi + "/convert"), "{}"), admin));
        return jdbc.queryForObject("select max(id) from sales_order where tenant_id = 0 and pi_id = ?", Long.class, pi);
    }

    private long soItem(long so) {
        return jdbc.queryForObject("select id from sales_order_item where so_id = ? order by line_no limit 1", Long.class, so);
    }

    private long afterSales(Long so, long customer) throws Exception {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("customerId", customer);
        b.put("soId", so);
        b.put("items", so == null ? List.of(Map.of("model", "X-9", "quantity", 1)) : List.of(Map.of("soItemId", soItem(so), "quantity", 1)));
        b.put("symptom", "伺服报警 AL.16");
        return ok(call(json(post(AS), write(b)), admin)).path("id").asLong();
    }

    private JsonNode replace(long asId, List<Map<String, Object>> items) throws Exception {
        return call(json(post(AS + "/" + asId + "/replacement"), write(Map.of("items", items, "remark", "换新"))), admin);
    }

    private static Map<String, Object> manual(String model, int qty, String price) {
        Map<String, Object> m = new HashMap<>();
        m.put("model", model);
        m.put("brand", "MITSUBISHI");
        m.put("quantity", qty);
        m.put("declaredPrice", price);
        return m;
    }

    private JsonNode order(long id) throws Exception {
        return ok(call(get(SO + "/" + id), admin));
    }

    @Test
    void replacementOrder_noReceipt_costToOriginal_statsExcluded_closeGuard() throws Exception {
        long so = paidOrder();
        long customer = jdbc.queryForObject("select customer_id from sales_order where id = ?", Long.class, so);
        long item = soItem(so);
        long asId = afterSales(so, customer);

        // 校验：没有订单不能补发；数量不超过订单；手填型号要申报单价
        long noOrder = afterSales(null, customer);
        assertEquals("没有订单的售后单不能补发", fail(replace(noOrder, List.of(manual("X-9", 1, "10")))).path("message").asText());
        assertEquals("请至少选择一个补发型号", fail(replace(asId, List.of())).path("message").asText());
        assertEquals("A-1 的补发数量不能超过订单数量 10", fail(replace(asId, List.of(Map.of("soItemId", item, "quantity", 11))))
                .path("message").asText());
        assertEquals("请填写 MR-J3ENCBL2M 的申报单价", fail(replace(asId, List.of(manual("MR-J3ENCBL2M", 1, null))))
                .path("message").asText());
        assertEquals("MR-J3ENCBL2M 的申报单价需要大于 0", fail(replace(asId, List.of(manual("MR-J3ENCBL2M", 1, "0"))))
                .path("message").asText());
        assertEquals("第 1 个型号的数量需要大于 0", fail(replace(asId, List.of(manual("MR-J3ENCBL2M", 0, "15"))))
                .path("message").asText());

        // 补发：原订单型号默认原单价，手填配件按申报单价
        JsonNode a = ok(replace(asId, List.of(Map.of("soItemId", item, "quantity", 1), manual("MR-J3ENCBL2M", 1, "15"))));
        JsonNode brief = a.path("replacements").get(0);
        long rep = brief.path("soId").asLong();
        assertEquals("A-1 × 1、MR-J3ENCBL2M × 1", brief.path("summary").asText());
        money("65.00", brief.path("declaredAmount"));
        assertFalse(brief.path("shipped").asBoolean());
        assertTrue(a.path("logs").toString().contains("补发订单 " + brief.path("soNo").asText() + "：A-1 × 1、MR-J3ENCBL2M × 1"));

        JsonNode r = order(rep);
        assertEquals(3, r.path("source").asInt());
        money("0", r.path("totalAmount"));
        assertEquals("无需收款", r.path("receiptStatusName").asText());
        assertEquals(asId, r.path("afterSalesId").asLong());
        assertEquals(so, r.path("originalSoId").asLong());
        assertEquals(order(so).path("soNo").asText(), r.path("originalSoNo").asText());
        money("50", r.path("items").get(0).path("declaredPrice"));
        money("0", r.path("items").get(0).path("unitPrice"));
        assertEquals(2, requirementIds(rep).size(), "照常生成采购需求");
        assertEquals(so, r.path("cost").path("replacementOfId").asLong());
        assertFalse(r.path("cost").hasNonNull("profit"), "补发订单不算毛利");

        // 不能登记收款
        Map<String, Object> receipt = new LinkedHashMap<>();
        receipt.put("amount", "10");
        receipt.put("receiptDate", LocalDate.now().toString());
        receipt.put("bankAccountId", jdbc.queryForObject("select min(id) from tenant_bank_account where tenant_id = 0", Integer.class));
        assertEquals("售后补发订单无需收款", fail(call(json(post(SO + "/" + rep + "/receipts"), write(receipt)), admin)).path("message").asText());

        // 成本计入原订单：A-1 未下单按成本价 ¥200，配件没有成本价
        JsonNode cost = order(so).path("cost");
        money("200", cost.path("replacementCost"));
        assertEquals(1, cost.path("replacements").size());
        money("200", cost.path("replacements").get(0).path("purchase"));
        assertFalse(cost.path("settled").asBoolean());
        assertTrue(cost.path("pending").toString().contains("补发订单 " + brief.path("soNo").asText() + " 还没结算"));
        money("2200", cost.path("cost"));

        // 列表：来源筛选与原订单号；补发订单不计入本月订单数与统计
        JsonNode page = ok(call(json(post(SO + "/page"), write(Map.of("source", 3))), admin));
        assertEquals(1, page.path("total").asInt());
        assertEquals(order(so).path("soNo").asText(), page.path("records").get(0).path("originalSoNo").asText());
        // 来源单号：补发订单列表带售后单 ID 与单号
        assertEquals(asId, page.path("records").get(0).path("afterSalesId").asLong());
        assertEquals(jdbc.queryForObject("select as_no from after_sales where id = ?", String.class, asId),
                page.path("records").get(0).path("afterSalesNo").asText());
        assertEquals(1, ok(call(get(SO + "/stats"), admin)).path("monthCount").asInt());
        JsonNode profit = ok(call(get("/api/v1/finance/profit-stats"), admin));
        assertEquals(1, profit.path("summary").path("orderCount").asInt());
        String from = LocalDate.now().withDayOfMonth(1).toString();
        JsonNode customers = ok(call(get("/api/v1/sales/stats").param("dateFrom", from).param("view", "CUSTOMER"), admin)).path("customers");
        assertEquals(1, customers.get(0).path("orders").asInt());

        // 结案：补发未出运不能结案；取消后可以
        Map<String, Object> close = Map.of("reasonCode", "QUALITY", "result", "换新");
        assertEquals("补发订单 " + brief.path("soNo").asText() + " 还没出运",
                fail(call(json(post(AS + "/" + asId + "/close"), write(close)), admin)).path("message").asText());
        ok(call(json(post(SO + "/" + rep + "/cancel"), write(Map.of("reason", "型号选错"))), admin));
        assertFalse(order(so).path("cost").hasNonNull("replacements"),
                "取消后不再计入");
        JsonNode closed = ok(call(json(post(AS + "/" + asId + "/close"), write(close)), admin));
        assertEquals("已结案", closed.path("statusName").asText());
        assertEquals("已取消", closed.path("replacements").get(0).path("progressName").asText());
    }

    @Test
    void shippedReplacement_allowsClose_andReplacementOfReplacementPointsToOriginal() throws Exception {
        long so = paidOrder();
        long customer = jdbc.queryForObject("select customer_id from sales_order where id = ?", Long.class, so);
        long asId = afterSales(so, customer);
        long rep = ok(replace(asId, List.of(Map.of("soItemId", soItem(so), "quantity", 2, "declaredPrice", "48.5"))))
                .path("replacements").get(0).path("soId").asLong();
        money("48.50", order(rep).path("items").get(0).path("declaredPrice"));
        jdbc.update("update sales_order set progress_code = 'SHIPPED' where id = ?", rep);
        JsonNode closed = ok(call(json(post(AS + "/" + asId + "/close"), write(Map.of("reasonCode", "QUALITY", "result", "换新"))), admin));
        assertTrue(closed.path("replacements").get(0).path("shipped").asBoolean());

        // 补发的货又出问题：再补发，成本仍计入最初的订单
        long as2 = afterSales(rep, customer);
        long rep2 = ok(replace(as2, List.of(Map.of("soItemId", soItem(rep), "quantity", 1)))).path("replacements").get(0).path("soId").asLong();
        assertEquals(so, order(rep2).path("originalSoId").asLong());
        money("48.50", order(rep2).path("items").get(0).path("declaredPrice"));
        assertEquals(2, order(so).path("cost").path("replacements").size());
    }
}
