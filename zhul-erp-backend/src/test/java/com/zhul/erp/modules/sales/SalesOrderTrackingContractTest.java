package com.zhul.erp.modules.sales;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** spec sales/sales-order：销售日期、手动建单、型号进度与采购员、现货 / 期货、订单毛利；sales/payment-receipt：手动订单的收款 */
class SalesOrderTrackingContractTest extends SalesContractSupport {

    private static final long ZHANG = 99000101L;
    private static final long LI = 99000102L;
    private static final String FIN = "/api/v1/finance/receipts";

    @BeforeEach
    void users() {
        cleanupUsers();
        jdbc.update("insert into user_basic (id, tenant_id, name, username, role_code) values (?, 0, '张三', 'it_so_zhang', ''), (?, 0, '李四', 'it_so_li', '')",
                ZHANG, LI);
    }

    @AfterEach
    void cleanupUsers() {
        jdbc.update("delete from user_basic where id in (?, ?)", ZHANG, LI);
        jdbc.update("delete from sourcing_quote where tenant_id = 0 and quoted_by in (?, ?)", ZHANG, LI);
    }

    private JsonNode convert(long piId, String salesDate) throws Exception {
        return call(json(post(PI + "/" + piId + "/convert"), salesDate == null ? "{}" : write(Map.of("salesDate", salesDate))), admin);
    }

    private JsonNode order(long id) throws Exception {
        return ok(call(get(SO + "/" + id), admin));
    }

    private JsonNode items(long soId, String action, Map<String, Object> body) throws Exception {
        return call(json(post(SO + "/" + soId + "/" + action), write(body)), admin);
    }

    private static Map<String, Object> body(List<Long> itemIds, String key, Object value) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("itemIds", itemIds);
        m.put(key, value);
        return m;
    }

    private static List<Long> itemIds(JsonNode so) {
        List<Long> ids = new ArrayList<>();
        so.path("items").forEach(i -> ids.add(i.path("id").asLong()));
        return ids;
    }

    /** 报价行的采购成本价取自某位采购的回价 */
    private void costFrom(long quotationItemId, long purchaser) {
        long inquiryItem = jdbc.queryForObject("select inquiry_item_id from quotation_item where id = ?", Long.class, quotationItemId);
        jdbc.update("insert into sourcing_quote (tenant_id, inquiry_item_id, quoted_by) values (0, ?, ?)", inquiryItem, purchaser);
        long quote = jdbc.queryForObject("select max(id) from sourcing_quote where inquiry_item_id = ?", Long.class, inquiryItem);
        jdbc.update("update quotation_item set cost_quote_id = ? where id = ?", quote, quotationItemId);
    }

    private JsonNode manual(long customer, String currency, List<Map<String, Object>> lines) throws Exception {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("customerId", customer);
        if (currency != null) {
            b.put("currencyCode", currency);
        }
        b.put("items", lines);
        return call(json(post(SO), write(b)), admin);
    }

    private static Map<String, Object> line(String model, int qty, String price, String cost, Long purchaser, Integer stock) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("model", model);
        m.put("quantity", qty);
        m.put("unitPrice", price);
        m.put("costPrice", cost);
        m.put("purchaserId", purchaser);
        m.put("stockType", stock);
        return m;
    }

    @Test
    void convert_salesDateDefaultsToSlip_purchaserAndStockFromQuotation() throws Exception {
        long c = customer("ACROBOT", "India");
        long q = quotation(c, 2, "USD", null, l("2198-E4020-ERS", 1, "4000", "740.44"), l("6ES7321-1FH00-0AA0", 2, "1000", "180.00"),
                l("6ES7153-1AA03-0XB0", 2, "300", "54.39"));
        List<Long> lines = quotationItemIds(q);
        jdbc.update("update quotation_item set lead_time = 7 where id in (?, ?)", lines.get(0), lines.get(1));
        costFrom(lines.get(0), ZHANG);
        costFrom(lines.get(1), ZHANG);
        costFrom(lines.get(2), LI);
        long pi = sentPi(lines, null);
        ok(uploadSlip(pi, "667.00", "2026-10-07", admin));
        assertEquals("销售日期不能晚于今天", fail(convert(pi, LocalDate.now().plusDays(1).toString())).path("message").asText());

        JsonNode so = ok(convert(pi, null));
        assertEquals("2026-10-07", so.path("salesDate").asText(), "默认取最早的水单付款日期");
        assertEquals(1, so.path("source").asInt());
        assertEquals(2, so.path("stockType").asInt(), "有期货型号 → 期货");
        assertEquals("待采购", so.path("progressName").asText());
        assertEquals("张三", so.path("items").get(0).path("purchaserName").asText());
        assertEquals(2, so.path("items").get(0).path("stockType").asInt());
        assertEquals(1, so.path("items").get(2).path("stockType").asInt(), "货期现货 → 现货");
        assertEquals("李四", so.path("items").get(2).path("purchaserName").asText());
        assertEquals(2, so.path("purchasers").size());
        assertEquals(2, so.path("purchasers").get(0).path("itemCount").asInt());

        JsonNode page = ok(call(json(post(SO + "/page"), write(Map.of("purchaserId", LI))), admin));
        assertEquals(1, page.path("total").asInt());
        assertEquals(List.of("张三", "李四"), objectMapper.convertValue(page.path("records").get(0).path("purchaserNames"), List.class));
        assertEquals(0, ok(call(json(post(SO + "/page"), write(Map.of("salesFrom", "2026-10-08", "salesTo", "2026-10-08"))), admin))
                .path("total").asInt(), "按销售日期筛选");
    }

    @Test
    void progress_perItem_orderTakesSlowest_completeOnlyAfterShipped() throws Exception {
        long c = customer("Siam", "Thailand");
        JsonNode so = ok(manual(c, null, List.of(line("A-1", 1, "100", "500", ZHANG, null), line("B-2", 1, "100", "500", ZHANG, null))));
        long id = so.path("id").asLong();
        List<Long> ids = itemIds(so);
        assertEquals("订单进度不存在或已停用", fail(items(id, "progress", body(ids, "progressCode", "NOPE"))).path("message").asText());

        // 「待采购」「已下单」由采购单推进：不能手动选，没下单的不能跳到后面
        assertEquals("「待采购」「已下单」「已入库」由采购与入库自动推进，不能手动选择",
                fail(items(id, "progress", body(ids, "progressCode", "ORDERED"))).path("message").asText());
        assertEquals("A-1 还有 1 个没有下单", fail(items(id, "progress", body(List.of(ids.get(0)), "progressCode", "TO_FORWARDER")))
                .path("message").asText());
        long sup = supplier("华控自动化", null);
        List<Long> reqs = requirementIds(id);
        long draft = generate(sup, List.of(reqs.get(0)), admin);
        assertEquals("待采购", order(id).path("items").get(0).path("progressName").asText(), "草稿不推进进度");
        priceAndConfirm(draft, "90", admin);
        so = order(id);
        assertEquals("已下单", so.path("items").get(0).path("progressName").asText(), "确认下单后自动变为已下单");
        assertEquals(1, so.path("items").get(0).path("purchaseOrderedQty").asInt());

        assertEquals("A-1 还有 1 个没有入库", fail(items(id, "progress", body(List.of(ids.get(0)), "progressCode", "TO_FORWARDER")))
                .path("message").asText());
        received(ids.get(0));
        so = ok(items(id, "progress", body(List.of(ids.get(0)), "progressCode", "TO_FORWARDER")));
        assertEquals("待采购", so.path("progressName").asText(), "取最靠前的型号");
        priceAndConfirm(generate(sup, List.of(reqs.get(1)), admin), "95", admin);
        so = order(id);
        assertEquals("已下单", so.path("progressName").asText());
        assertEquals("已交货代", so.path("items").get(0).path("progressName").asText());
        assertFalse(so.path("completable").asBoolean());

        received(ids.get(1));
        ok(items(id, "progress", body(ids, "progressCode", "TO_FORWARDER")));
        assertEquals("还有 2 个型号还没到「已出运」，不能确认收货", fail(call(post(SO + "/" + id + "/complete"), admin)).path("message").asText());
        so = ok(items(id, "progress", body(ids, "progressCode", "SHIPPED")));
        assertTrue(so.path("completable").asBoolean());
        so = ok(call(post(SO + "/" + id + "/complete"), admin));
        assertEquals("已完成", so.path("progressName").asText());
        assertFalse(so.path("trackable").asBoolean());
        assertEquals("订单已完成，不能修改", fail(items(id, "progress", body(ids, "progressCode", "SHIPPED"))).path("message").asText());
        assertEquals("订单已完成，不能取消", fail(call(json(post(SO + "/" + id + "/cancel"), "{\"reason\":\"x\"}"), admin))
                .path("message").asText());
        assertEquals(1, ok(call(json(post(SO + "/page"), write(Map.of("progressCode", "COMPLETED"))), admin)).path("total").asInt());
    }

    /** 订单型号行合格入库 1 个（直接写入库单，进度推进另由入库联动测试覆盖） */
    private void received(long soItemId) {
        jdbc.update("insert into purchase_receipt (tenant_id, gr_no, status) values (0, ?, 1)", "GRIT" + soItemId);
        long gr = jdbc.queryForObject("select max(id) from purchase_receipt where tenant_id = 0", Long.class);
        jdbc.update("insert into purchase_receipt_item (tenant_id, receipt_id, so_item_id, shipped_qty, received_qty, qualified_qty) "
                + "values (0, ?, ?, 1, 1, 1)", gr, soItemId);
    }

    @Test
    void purchaserAndStock_editable_logged_stats() throws Exception {
        long c = customer("Nordic", "Sweden");
        JsonNode so = ok(manual(c, null, List.of(line("A-1", 1, "100", null, ZHANG, 1), line("B-2", 1, "100", null, null, 1))));
        long id = so.path("id").asLong();
        List<Long> ids = itemIds(so);
        assertEquals(1, so.path("stockType").asInt());
        JsonNode stats = ok(call(get(SO + "/stats"), admin));
        assertEquals(1, stats.path("unassignedCount").asInt(), "有型号未指定采购员");

        so = ok(items(id, "purchaser", body(ids, "purchaserId", LI)));
        assertEquals("李四", so.path("items").get(1).path("purchaserName").asText());
        assertEquals(1, so.path("purchasers").size());
        assertEquals("采购员不存在", fail(items(id, "purchaser", body(ids, "purchaserId", 1L))).path("message").asText());
        so = ok(items(id, "stock-type", body(List.of(ids.get(1)), "stockType", 2)));
        assertEquals(2, so.path("stockType").asInt(), "任一期货 → 期货");
        assertEquals("型号不属于这张订单", fail(items(id, "stock-type", body(List.of(-1L), "stockType", 2))).path("message").asText());
        so = ok(call(json(post(SO + "/" + id + "/sales-date"), "{\"salesDate\":\"2026-09-30\"}"), admin));
        assertEquals("2026-09-30", so.path("salesDate").asText());
        assertTrue(jdbc.queryForObject("select count(*) from sys_log where tenant_id = 0 and content like ?", Integer.class,
                "%A-1 采购员 张三 → 李四%") > 0, "改采购员写操作日志");
        assertEquals(0, ok(call(get(SO + "/stats"), admin)).path("unassignedCount").asInt());
    }

    @Test
    void manualCreate_requiredOnly_rate_customerType() throws Exception {
        long c = customer("Hoorain HTF", "Pakistan");
        assertEquals("请至少添加一个型号", fail(manual(c, null, List.of())).path("message").asText());
        assertTrue(fail(manual(c, "RUB", List.of(line("A-1", 1, "10", null, null, null)))).path("message").asText().contains("RUB 汇率"));
        JsonNode so = ok(manual(c, null, List.of(line("6ES7321-1FH00-0AA0", 2, "59.81", null, null, null))));
        assertEquals("USD", so.path("currencyCode").asText(), "默认美元");
        assertEquals(LocalDate.now().toString(), so.path("salesDate").asText(), "默认当天");
        assertEquals(2, so.path("source").asInt());
        assertEquals(1, so.path("customerType").asInt(), "之前没有有效订单 → 新客户");
        money("119.62", so.path("totalAmount"));
        money("855.28", so.path("totalAmountCny"));
        assertTrue(so.path("piId").isNull() || so.path("piId").isMissingNode());
        assertEquals("待采购", so.path("progressName").asText());
        assertEquals(1, so.path("margin").path("missingCostCount").asInt());
        JsonNode second = ok(manual(c, null, List.of(line("X", 1, "1", null, null, null))));
        assertEquals(2, second.path("customerType").asInt(), "已有有效订单 → 老客户");
        JsonNode list = ok(call(json(post(SO + "/page"), "{}"), admin)).path("records").get(0);
        assertEquals("未付款", list.path("receiptStatusName").asText());
    }

    @Test
    void manualOrderReceipts_platformClaimMargin_cancelledRejects() throws Exception {
        bankAccount("USD");
        long c = customer("Hoorain HTF", "Pakistan");
        JsonNode so = ok(manual(c, null, List.of(line("A-1", 10, "100", "650.00", null, null))));
        long id = so.path("id").asLong();
        money("1000.00", so.path("totalAmount"));

        Map<String, Object> platform = new LinkedHashMap<>();
        platform.put("paymentMethod", "ALIBABA_TA");
        platform.put("platformOrderNo", "SO-TA-1");
        platform.put("amount", "300");
        platform.put("platformFee", "7.50");
        platform.put("receiptDate", LocalDate.now().toString());
        so = ok(call(json(post(SO + "/" + id + "/platform-receipts"), write(platform)), admin));
        assertEquals("部分到账", so.path("receiptStatusName").asText());
        assertTrue(so.path("margin").path("estimated").asBoolean());
        // 预计毛利 = 7150.00 − 6500.00
        money("650.00", so.path("margin").path("profitCny"));

        Map<String, Object> unclaimed = new LinkedHashMap<>();
        unclaimed.put("currencyCode", "USD");
        unclaimed.put("amount", "700");
        unclaimed.put("receiptDate", LocalDate.now().toString());
        unclaimed.put("bankAccountId", jdbc.queryForObject("select min(id) from tenant_bank_account where tenant_id = 0", Integer.class));
        unclaimed.put("payer", "HOORAIN");
        long rid = ok(call(json(post(FIN + "/unclaimed"), write(unclaimed)), admin)).path("id").asLong();
        assertEquals(1, ok(call(get(SO + "/" + id + "/claimable-receipts"), admin)).size());
        so = ok(call(json(post(SO + "/" + id + "/claim"), write(Map.of("receiptId", rid))), admin));
        assertEquals("已到账", so.path("receiptStatusName").asText());
        assertEquals(0, ok(call(get(FIN + "/unclaimed"), admin)).path("unclaimed").size(), "认领到订单后不再是未认领");
        assertFalse(so.path("margin").path("estimated").asBoolean());
        // 实收人民币 = (300 − 7.50) × 7.15 + 700 × 7.15 = 2091.38 + 5005.00；毛利 = 7096.38 − 6500.00
        money("596.38", so.path("margin").path("profitCny"));
        assertEquals("阿里巴巴信用保障", so.path("methodTotals").get(0).path("paymentMethodName").asText(), "线上在前");
        money("700.00", so.path("methodTotals").get(1).path("amount"));

        JsonNode records = ok(call(json(post(FIN + "/records"), "{}"), admin)).path("records");
        assertEquals("SO" + TODAY + "001", records.get(0).path("soNo").asText());

        assertEquals("这笔到账不是认领来的，不能取消认领", fail(call(json(post(FIN + "/" + so.path("receipts").get(0).path("id").asLong() + "/unclaim"),
                "{\"reason\":\"x\"}"), admin)).path("message").asText());
        ok(call(json(post(FIN + "/" + rid + "/unclaim"), "{\"reason\":\"认领错\"}"), admin));
        assertEquals("部分到账", order(id).path("receiptStatusName").asText(), "取消认领后订单重算");

        ok(call(json(post(SO + "/" + id + "/cancel"), "{\"reason\":\"客户取消\"}"), admin));
        platform.put("platformOrderNo", "SO-TA-2");
        assertEquals("订单已取消，不能登记收款", fail(call(json(post(SO + "/" + id + "/platform-receipts"), write(platform)), admin))
                .path("message").asText());
        assertEquals(1, order(id).path("methodTotals").size(), "已有收款保留在已取消的订单上");
    }

    @Test
    void piOrder_receiptsStayOnPi() throws Exception {
        long c = customer("ACROBOT", "India");
        long pi = sentPi(quotationItemIds(quotation(c, 2, "USD", null, l("A-1", 1, "100", "200"))), null);
        ok(uploadSlip(pi, "100", "2026-10-07", admin));
        long id = ok(convert(pi, "2026-10-08")).path("id").asLong();
        Map<String, Object> platform = new LinkedHashMap<>();
        platform.put("paymentMethod", "ALIBABA_TA");
        platform.put("platformOrderNo", "PI-TA-1");
        platform.put("amount", "100");
        platform.put("receiptDate", LocalDate.now().toString());
        assertEquals("这张订单由 PI 转成，收款请在 PI 上登记", fail(call(json(post(SO + "/" + id + "/platform-receipts"), write(platform)), admin))
                .path("message").asText());
    }

    @Test
    void candidatePis_onlyConvertible() throws Exception {
        long c = customer("ACROBOT", "India");
        long paid = sentPi(quotationItemIds(quotation(c, 2, "USD", null, l("A-1", 1, "100", "200"))), null);
        ok(uploadSlip(paid, "200", "2026-10-07", admin));
        long unpaid = sentPi(quotationItemIds(quotation(c, 2, "USD", null, l("B-1", 1, "100", "200"))), null);
        long converted = sentPi(quotationItemIds(quotation(c, 2, "USD", null, l("C-1", 1, "100", "200"))), null);
        ok(uploadSlip(converted, "200", "2026-10-07", admin));
        ok(convert(converted, null));

        JsonNode list = ok(call(get(SO + "/candidates/pis"), admin));
        assertEquals(1, list.size(), "没有水单的、已转订单的不列出：" + list);
        assertEquals(paid, list.get(0).path("id").asLong());
        assertEquals(1, list.get(0).path("lastKind").asInt(), "最近一笔是水单");
        money("200", list.get(0).path("lastAmount"));
        assertEquals(1, ok(call(get(SO + "/candidates/pis").param("keyword", "ACRO"), admin)).size());
        assertEquals(0, ok(call(get(SO + "/candidates/pis").param("keyword", "nobody"), admin)).size());
        assertTrue(unpaid > 0);
    }
}
