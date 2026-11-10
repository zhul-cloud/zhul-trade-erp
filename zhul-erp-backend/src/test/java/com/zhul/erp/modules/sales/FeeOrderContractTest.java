package com.zhul.erp.modules.sales;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhul.erp.modules.sales.constants.SalesConstants;
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

/** 费用订单（spec sales/sales-order「费用订单」、finance/order-cost「费用订单的收入与毛利」） */
class FeeOrderContractTest extends SalesContractSupport {

    @Test
    void feeOrderLifecycle() throws Exception {
        bankAccount("USD");
        long customer = customer("Fee Buyer", "US");
        long original = manualOrder(customer);
        jdbc.update("update sales_order set business_unit_id = 7 where id = ?", original);

        // 没有费用行、金额为 0 都不行
        fail(call(json(post(SO + "/" + original + "/fee-orders"), write(Map.of("fees", List.of()))), admin));
        JsonNode fee = ok(call(json(post(SO + "/" + original + "/fee-orders"),
                write(Map.of("fees", List.of(Map.of("feeName", "Freight", "amount", "180")), "remark", "改走我们货代"))), admin));
        long feeId = fee.path("id").asLong();
        assertEquals(SalesConstants.SO_FEE, fee.path("source").asInt());
        assertEquals(original, fee.path("originalSoId").asLong());
        assertEquals(0, fee.path("items").size());
        money("180", fee.path("totalAmount"));
        assertEquals("USD", fee.path("currencyCode").asText());
        assertEquals("待收款", fee.path("progressName").asText());
        assertFalse(fee.path("trackable").asBoolean());
        assertFalse(fee.path("completable").asBoolean());
        assertFalse(fee.path("partialCancelable").asBoolean());
        assertEquals(customer, jdbc.queryForObject("select customer_id from sales_order where id = ?", Long.class, feeId));
        assertEquals(7, jdbc.queryForObject("select business_unit_id from sales_order where id = ?", Integer.class, feeId));
        // 不生成采购需求
        assertEquals(0, requirementIds(feeId).size());

        // 费用订单、补发订单都不能再建费用订单
        fail(call(json(post(SO + "/" + feeId + "/fee-orders"), write(Map.of("fees", List.of(Map.of("feeName", "X", "amount", "1"))))), admin));
        // 手动推进、确认收货、部分取消都拒绝
        assertEquals("费用订单没有型号进度，收款到账后自动完成",
                fail(call(json(post(SO + "/" + feeId + "/complete"), "{}"), admin)).path("message").asText());
        assertEquals("费用订单没有型号，不能部分取消，可以整单取消或修改费用", fail(call(json(post(SO + "/" + feeId + "/partial-cancel"),
                write(Map.of("reason", "OTHER", "items", List.of(Map.of("itemId", 1, "quantity", 1))))), admin)).path("message").asText());

        // 原订单详情列出费用订单；列表显示「费用订单 1」与原订单号
        JsonNode orig = ok(call(get(SO + "/" + original), admin));
        assertEquals(1, orig.path("feeOrders").size());
        assertEquals(feeId, orig.path("feeOrders").get(0).path("id").asLong());
        JsonNode page = ok(call(json(post(SO + "/page"), write(Map.of("page", 1, "pageSize", 20))), admin));
        assertEquals(2, page.path("records").size());
        for (JsonNode r : page.path("records")) {
            if (r.path("id").asLong() == original) {
                assertEquals(1, r.path("feeOrderCount").asInt());
            }
            if (r.path("id").asLong() == feeId) {
                assertEquals("待收款", r.path("progressName").asText());
            }
        }
        // 「待采购」筛选不含费用订单；按来源可以单独筛
        JsonNode pending = ok(call(json(post(SO + "/page"), write(Map.of("page", 1, "pageSize", 20, "progressCode", "PENDING_PURCHASE"))), admin));
        assertEquals(1, pending.path("total").asInt());
        assertEquals(original, pending.path("records").get(0).path("id").asLong());
        JsonNode bySource = ok(call(json(post(SO + "/page"), write(Map.of("page", 1, "pageSize", 20, "source", 4))), admin));
        assertEquals(1, bySource.path("total").asInt());

        // 收款到账后自动完成，作废后恢复
        ok(call(json(post(SO + "/" + feeId + "/receipts"), write(receipt("180"))), admin));
        long receiptId = jdbc.queryForObject("select max(id) from payment_receipt where so_id = ?", Long.class, feeId);
        JsonNode paid = ok(call(get(SO + "/" + feeId), admin));
        assertEquals("COMPLETED", paid.path("progressCode").asText());
        assertTrue(jdbc.queryForObject("select completed_at is not null from sales_order where id = ?", Boolean.class, feeId));
        // 收入按到账、成本 0；结算只看收款与结汇
        JsonNode cost = paid.path("cost");
        money("0", cost.path("cost"));
        assertEquals(0, cost.path("revenue").decimalValue().compareTo(cost.path("profit").decimalValue()));
        cost.path("pending").forEach(x -> assertEquals("收款还没结汇", x.asText(), "费用订单只可能差结汇：" + cost.path("pending")));
        assertEquals(orig.path("soNo").asText(), cost.path("feeOfNo").asText());

        ok(call(json(post(SO + "/" + feeId + "/receipts/" + receiptId + "/void"), "{\"reason\":\"录错\"}"), admin));
        JsonNode reverted = ok(call(get(SO + "/" + feeId), admin));
        assertEquals("PENDING_PURCHASE", reverted.path("progressCode").asText());
        assertEquals("待收款", reverted.path("progressName").asText());
        assertTrue(jdbc.queryForObject("select completed_at is null from sales_order where id = ?", Boolean.class, feeId));

        // 履约看板不收录费用订单
        assertEquals(0, jdbc.queryForObject("select count(*) from followup_item where target_type = 5 and target_id = ?", Integer.class, feeId));

        // 原订单取消后不能再建
        jdbc.update("update sales_order set status = 2 where id = ?", original);
        fail(call(json(post(SO + "/" + original + "/fee-orders"), write(Map.of("fees", List.of(Map.of("feeName", "X", "amount", "1"))))), admin));
    }

    /** 手动订单：A-1 × 2，USD 100 */
    private long manualOrder(long customer) throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("customerId", customer);
        m.put("currencyCode", "USD");
        m.put("items", List.of(Map.of("productId", manualProduct("A-1"), "quantity", 2, "unitPrice", "100", "costPrice", "300")));
        return ok(call(json(post(SO), write(m)), admin)).path("id").asLong();
    }

    private Map<String, Object> receipt(String amount) {
        Map<String, Object> r = new HashMap<>();
        r.put("amount", amount);
        r.put("receiptDate", LocalDate.now().toString());
        r.put("bankAccountId", jdbc.queryForObject("select min(id) from tenant_bank_account where tenant_id = 0", Integer.class));
        return r;
    }
}
