package com.zhul.erp.modules.finance;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhul.erp.modules.finance.service.PayableBackfill;
import com.zhul.erp.modules.sales.SalesContractSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * spec finance/accounts-payable、finance/supplier-payment，与 purchase/purchase-order「付款条件」「采购单的付款」、
 * purchase/receiving-discrepancy（折价冲减应付）、logistics/forwarder-statement（对账确认生成应付）。
 */
class PayableContractTest extends SalesContractSupport {

    private static final String AP = "/api/v1/finance/payables";
    private static final String PY = "/api/v1/finance/payments";
    private static final String SHIP = "/api/v1/purchase/shipments";
    private static final String GR = "/api/v1/warehouse/receipts";
    private static final String DIFF = "/api/v1/purchase/discrepancies";
    private static final int MENU_PO = 100088;
    private static final int MENU_SHIP = 100089;
    private static final int[] BUYER_BUTTONS = {110189, 110190, 110191, 110192, 110205};

    @Autowired
    private PayableBackfill backfill;

    private String lin;
    private long account;

    @BeforeEach
    void people() {
        jdbc.update("insert into role_resource (role_code, resource_id, resource_code) values (?, ?, '')", BUYER_ROLE, MENU_PO);
        jdbc.update("insert into role_resource (role_code, resource_id, resource_code) values (?, ?, '')", BUYER_ROLE, MENU_SHIP);
        for (int id : BUYER_BUTTONS) {
            jdbc.update("insert into role_resource (role_code, resource_id, resource_code) values (?, ?, '')", BUYER_ROLE, id);
        }
        lin = token("it_lin");
        // PI 用最早的美元收款账户；公司付款账户为后建的人民币账户
        bankAccount("USD");
        jdbc.update("insert into tenant_bank_account (tenant_id, currency_code, bank_name, account_name, account_no, is_default) "
                + "values (0, 'CNY', '招商银行', '基本户', '6225888866886688', 0)");
        account = jdbc.queryForObject("select max(id) from tenant_bank_account where tenant_id = 0", Long.class);
    }

    // ---------------------------------------------------------------- 准备数据

    /** 确认下单的采购单：一行，单价 price；付款条件 terms（JSON 数组的 Java 形式） */
    private JsonNode orderedPo(long supplierId, String model, int qty, String price, List<Map<String, Object>> terms) throws Exception {
        long c = customer("ACROBOT", "India");
        List<Long> items = quotationItemIds(quotation(c, 2, "USD", null, l(model, qty, "100", "200")));
        for (Long i : items) {
            long inquiryItem = jdbc.queryForObject("select inquiry_item_id from quotation_item where id = ?", Long.class, i);
            jdbc.update("insert into sourcing_quote (tenant_id, inquiry_item_id, quoted_by, supplier_id, channel, shop_name) values (0, ?, ?, ?, 4, '')",
                    inquiryItem, BUYER_LIN, supplierId);
            jdbc.update("update quotation_item set cost_quote_id = (select max(id) from sourcing_quote where inquiry_item_id = ?) where id = ?",
                    inquiryItem, i);
        }
        long pi = sentPi(items, null);
        ok(uploadSlip(pi, "1.00", "2026-10-07", admin));
        ok(call(json(post(PI + "/" + pi + "/convert"), "{}"), admin));
        long draft = jdbc.queryForObject("select max(id) from purchase_order where tenant_id = 0 and supplier_id = ? and status = 1 "
                + "and deleted_at is null", Long.class, supplierId);
        Map<String, Object> body = poBody(po(draft, admin), price);
        body.put("paymentTerms", terms);
        ok(savePo(draft, body, admin));
        return ok(confirmPo(draft, admin));
    }

    /** 空值不输出：缺失或 null 都算空 */
    private static boolean blank(JsonNode n) {
        return n.isMissingNode() || n.isNull();
    }

    private static Map<String, Object> term(int percent, int trigger, int days) {
        return Map.of("percent", percent, "trigger", trigger, "days", days);
    }

    private JsonNode bySource(long poId, String token) throws Exception {
        return ok(call(get(AP + "/by-source").param("sourceType", "1").param("sourceId", String.valueOf(poId)), token));
    }

    private long screenshot(String token) throws Exception {
        return ok(call(multipart("/api/v1/attachments").file(new MockMultipartFile("file", "回单.png", "image/png", PNG))
                .param("ownerType", "PAYMENT"), token)).path("id").asLong();
    }

    private Map<String, Object> payBody(int method, String amount, Map<Long, String> allocations, String token) throws Exception {
        Map<String, Object> b = new HashMap<>();
        b.put("method", method);
        if (method == 1) {
            b.put("bankAccountId", account);
        }
        b.put("payDate", LocalDate.now().toString());
        b.put("amount", new BigDecimal(amount));
        b.put("attachmentIds", List.of(screenshot(token)));
        List<Map<String, Object>> lines = new ArrayList<>();
        allocations.forEach((k, v) -> lines.add(Map.of("payableId", k, "amount", new BigDecimal(v))));
        b.put("allocations", lines);
        b.put("idempotencyKey", UUID.randomUUID().toString());
        return b;
    }

    private JsonNode pay(Map<String, Object> body, String token) throws Exception {
        return call(json(post(PY), write(body)), token);
    }

    // ---------------------------------------------------------------- 生成与付款计划

    @Test
    void confirmCreatesPayable_installments_companyPayment_dueAfterReceipt() throws Exception {
        long sup = supplier("深圳工控", null);
        JsonNode po = orderedPo(sup, "A-1", 10, "1000", List.of(term(30, 1, 0), term(70, 3, 0)));
        long poId = po.path("id").asLong();

        JsonNode ap = bySource(poId, lin);
        assertEquals("AP" + TODAY + "001", ap.path("payableNo").asText(), "内部单据不带前缀");
        assertEquals("深圳工控", ap.path("payeeName").asText());
        assertEquals(1, ap.path("payeeType").asInt());
        money("10000.00", ap.path("amount"));
        assertEquals(1, ap.path("status").asInt());
        assertEquals(2, ap.path("installments").size());
        money("3000.00", ap.path("installments").get(0).path("amount"));
        assertEquals(LocalDate.now().toString(), ap.path("installments").get(0).path("dueDate").asText());
        money("7000.00", ap.path("installments").get(1).path("amount"));
        assertTrue(blank(ap.path("installments").get(1).path("dueDate")), "入库后的期次待入库");
        assertEquals("30% 下单后 · 70% 入库后", ap.path("paymentTerms").asText());
        long apId = ap.path("id").asLong();

        // 财务登记预付：核销第 1 期
        JsonNode paid = ok(pay(payBody(1, "3000.00", Map.of(apId, "3000.00"), admin), admin));
        assertEquals("PY" + TODAY + "001", paid.path("paymentNo").asText());
        ap = bySource(poId, admin);
        assertEquals(2, ap.path("status").asInt());
        assertEquals(3, ap.path("installments").get(0).path("status").asInt());
        money("7000.00", ap.path("unpaidAmount"));
        assertEquals(1, ap.path("payments").size());
        // 采购单列表：付款列与付款状态筛选
        JsonNode list = ok(call(json(post(PO + "/page"), write(Map.of("payStatus", "PARTIAL"))), admin));
        assertEquals(1, list.path("total").asInt());
        assertEquals(2, list.path("records").get(0).path("payStatus").asInt());
        money("3000.00", list.path("records").get(0).path("paidAmount"));
        assertEquals(0, ok(call(json(post(PO + "/page"), write(Map.of("payStatus", "PAID"))), admin)).path("total").asInt());

        // 全部入库后第 2 期有到期日
        long shipItem = po.path("items").get(0).path("id").asLong();
        JsonNode s = ok(call(json(post(SHIP), write(Map.of("poId", poId, "carrier", "顺丰", "trackingNo", "SF123456",
                "shipDate", LocalDate.now().toString(), "items", List.of(Map.of("poItemId", shipItem, "quantity", 10))))), lin));
        long shipmentId = s.path("shipment").path("id").asLong();
        JsonNode form = ok(call(get(GR + "/shipments/" + shipmentId), admin));
        ok(call(json(post(GR), write(Map.of("shipmentId", shipmentId, "receivedDate", LocalDate.now().toString(),
                "items", List.of(Map.of("shipmentItemId", form.path("shipment").path("items").get(0).path("id").asLong(),
                        "receivedQty", 10, "qualifiedQty", 10, "defectiveQty", 0))))), admin));
        ap = bySource(poId, admin);
        assertEquals(LocalDate.now().toString(), ap.path("installments").get(1).path("dueDate").asText());
        assertEquals(LocalDate.now().toString(), ap.path("nextDueDate").asText());

        // 列表、统计、筛选
        JsonNode page = ok(call(get(AP).param("due", "MONTH"), admin));
        assertEquals(1, page.path("total").asInt());
        JsonNode stats = ok(call(get(AP + "/stats"), admin));
        money("7000.00", stats.path("monthDue"));
        assertEquals(0, ok(call(get(AP).param("due", "OVERDUE"), admin)).path("total").asInt());
        // 采购员没有应付管理菜单
        assertFalse(call(get(AP), lin).path("code").asInt() == 0);
    }

    @Test
    void discountAndShortReduceAmount_cancelMakesCredit_refundAndOffset() throws Exception {
        long sup = supplier("苏州欧姆龙一级", null);
        JsonNode po = orderedPo(sup, "B-1", 10, "500", List.of(term(100, 1, 0)));
        long poId = po.path("id").asLong();
        long apId = bySource(poId, admin).path("id").asLong();
        ok(pay(payBody(1, "5000.00", Map.of(apId, "5000.00"), admin), admin));
        assertEquals(3, bySource(poId, admin).path("status").asInt());

        // 到货：实收 10、合格 8，不良 2 折价接收 ¥100；少发 0
        long poItem = po.path("items").get(0).path("id").asLong();
        JsonNode s = ok(call(json(post(SHIP), write(Map.of("poId", poId, "carrier", "顺丰", "trackingNo", "SF99887766",
                "shipDate", LocalDate.now().toString(), "items", List.of(Map.of("poItemId", poItem, "quantity", 10))))), lin));
        long shipmentId = s.path("shipment").path("id").asLong();
        JsonNode form = ok(call(get(GR + "/shipments/" + shipmentId), admin));
        ok(call(json(post(GR), write(Map.of("shipmentId", shipmentId, "receivedDate", LocalDate.now().toString(),
                "items", List.of(Map.of("shipmentItemId", form.path("shipment").path("items").get(0).path("id").asLong(),
                        "receivedQty", 10, "qualifiedQty", 8, "defectiveQty", 2))))), admin));
        long diff = jdbc.queryForObject("select id from receiving_discrepancy where po_id = ? and type = 2", Long.class, poId);
        ok(call(json(post(DIFF + "/" + diff + "/handle"), write(Map.of("resolution", 5, "discountAmount", 100, "note", "外壳划伤"))), lin));
        JsonNode ap = bySource(poId, admin);
        money("4900.00", ap.path("amount"));
        money("100.00", ap.path("creditAmount"));
        assertEquals(3, ap.path("status").asInt());

        // 退款 ¥40，剩余欠款 ¥60 抵扣另一张采购单
        Map<String, Object> refund = new HashMap<>();
        refund.put("payableId", apId);
        refund.put("amount", 40);
        refund.put("refundDate", LocalDate.now().toString());
        refund.put("bankAccountId", account);
        refund.put("attachmentIds", List.of(screenshot(admin)));
        refund.put("idempotencyKey", UUID.randomUUID().toString());
        ok(call(json(post(PY + "/refunds"), write(refund)), admin));
        money("60.00", bySource(poId, admin).path("creditAmount"));
        refund.put("amount", 100);
        refund.put("idempotencyKey", UUID.randomUUID().toString());
        assertEquals("退款不能超过欠款 60.00", fail(call(json(post(PY + "/refunds"), write(refund)), admin)).path("message").asText());

        JsonNode po2 = orderedPo(sup, "B-2", 1, "300", List.of(term(100, 1, 0)));
        long ap2 = bySource(po2.path("id").asLong(), admin).path("id").asLong();
        JsonNode open = ok(call(get(AP + "/" + ap2 + "/open"), admin));
        assertEquals(2, open.size(), "同一收款方有未付或欠款的都列出");
        Map<String, Object> body = payBody(1, "240.00", Map.of(ap2, "300.00"), admin);
        body.put("credits", List.of(Map.of("payableId", apId, "amount", new BigDecimal("60.00"))));
        JsonNode offset = ok(pay(body, admin));
        money("60.00", offset.path("creditUsed"));
        assertEquals(3, bySource(po2.path("id").asLong(), admin).path("status").asInt());
        money("0.00", bySource(poId, admin).path("creditAmount"));

        // 作废抵扣的那笔：两边恢复
        ok(call(json(post(PY + "/" + offset.path("id").asLong() + "/void"), write(Map.of("reason", "金额填错"))), admin));
        money("60.00", bySource(poId, admin).path("creditAmount"));
        assertEquals(1, bySource(po2.path("id").asLong(), admin).path("status").asInt());
        assertTrue(ok(call(get(PY).param("showVoid", "true").param("keyword", offset.path("paymentNo").asText()), admin))
                .path("records").get(0).path("voided").asBoolean());
        assertEquals(0, ok(call(get(PY).param("keyword", offset.path("paymentNo").asText()), admin)).path("total").asInt(),
                "作废的默认不显示");
    }

    @Test
    void cancelAfterPrepayClosesWithCredit() throws Exception {
        long sup = supplier("东莞汇川代理", null);
        JsonNode po = orderedPo(sup, "C-1", 2, "1000", List.of(term(30, 1, 0), term(70, 3, 0)));
        long poId = po.path("id").asLong();
        long apId = bySource(poId, admin).path("id").asLong();
        ok(pay(payBody(1, "600.00", Map.of(apId, "600.00"), admin), admin));
        ok(call(json(post(PO + "/" + poId + "/cancel"), "{\"reason\":\"供应商缺货\"}"), admin));
        JsonNode ap = bySource(poId, admin);
        assertEquals(4, ap.path("status").asInt());
        money("0.00", ap.path("amount"));
        money("600.00", ap.path("creditAmount"));
        assertTrue(blank(ap.path("nextDueDate")));
        // 已关闭但有欠款的默认仍显示
        assertEquals(1, ok(call(get(AP).param("hasCredit", "true"), admin)).path("total").asInt());
        // 已关闭的不能再核销
        assertEquals("应付单 " + ap.path("payableNo").asText() + " 已关闭，不能再核销",
                fail(pay(payBody(1, "1.00", Map.of(apId, "1.00"), admin), admin)).path("message").asText());
    }

    @Test
    void advancePayment_validation_idempotency_voidPermission() throws Exception {
        long sup = supplier("华控自动化", null);
        JsonNode po = orderedPo(sup, "D-1", 1, "860", List.of(term(100, 1, 0)));
        long poId = po.path("id").asLong();
        long apId = bySource(poId, lin).path("id").asLong();

        // 采购员不能登记公司对公
        assertEquals("没有「付款登记」权限，公司对公付款由财务登记",
                fail(pay(payBody(1, "860.00", Map.of(apId, "860.00"), lin), lin)).path("message").asText());
        // 超过未付
        assertEquals("应付单 " + bySource(poId, lin).path("payableNo").asText() + " 未付金额已变化，请刷新后重试",
                fail(pay(payBody(2, "900.00", Map.of(apId, "900.00"), lin), lin)).path("message").asText());
        // 没有截图
        Map<String, Object> noFile = payBody(2, "860.00", Map.of(apId, "860.00"), lin);
        noFile.put("attachmentIds", List.of());
        assertEquals("请上传付款截图", fail(pay(noFile, lin)).path("message").asText());
        // 合计不等
        assertEquals("核销合计须等于付款金额加欠款抵扣",
                fail(pay(payBody(2, "800.00", Map.of(apId, "860.00"), lin), lin)).path("message").asText());

        // 员工垫付，重复提交只生成一笔
        Map<String, Object> body = payBody(2, "860.00", Map.of(apId, "860.00"), lin);
        JsonNode first = ok(pay(body, lin));
        JsonNode again = ok(pay(body, lin));
        assertEquals(first.path("id").asLong(), again.path("id").asLong());
        assertEquals("it_lin", first.path("advanceUser").asText());
        assertEquals(1, first.path("reimburseStatus").asInt(), "进入待报销");
        assertEquals(1, jdbc.queryForObject("select count(*) from fin_payment where tenant_id = 0", Integer.class));
        assertEquals(3, bySource(poId, lin).path("status").asInt());

        // 付款记录：按垫付人筛选、统计
        JsonNode list = ok(call(get(PY).param("method", "2").param("advanceUser", "it_lin"), admin));
        assertEquals(1, list.path("total").asInt());
        JsonNode stats = ok(call(get(PY + "/stats"), admin));
        money("860.00", stats.path("advance"));
        money("0.00", stats.path("company"));

        // 登记人本人可以作废
        ok(call(json(post(PY + "/" + first.path("id").asLong() + "/void"), write(Map.of("reason", "重复"))), lin));
        assertEquals(1, bySource(poId, lin).path("status").asInt());
        assertEquals("付款记录已作废",
                fail(call(json(post(PY + "/" + first.path("id").asLong() + "/void"), write(Map.of("reason", "x"))), admin)).path("message").asText());
    }

    @Test
    void usdPaymentRecordsActualRate() throws Exception {
        long sup = supplier("Omron HK", null);
        JsonNode po = orderedPo(sup, "E-1", 1, "1000", List.of(term(100, 1, 0)));
        long poId = po.path("id").asLong();
        // 改为美元采购单：直接改库模拟外币下单（汇率 7.1）
        jdbc.update("update purchase_order set currency_code = 'USD', exchange_rate = 7.100000 where id = ?", poId);
        jdbc.update("update fin_payable set currency_code = 'USD', exchange_rate = 7.100000 where source_id = ? and source_type = 1", poId);
        long apId = bySource(poId, admin).path("id").asLong();
        Map<String, Object> body = payBody(1, "1000.00", Map.of(apId, "1000.00"), admin);
        assertEquals("外币付款请填写实付人民币", fail(pay(body, admin)).path("message").asText());
        body.put("amountCny", new BigDecimal("7180.00"));
        body.put("idempotencyKey", UUID.randomUUID().toString());
        JsonNode p = ok(pay(body, admin));
        assertEquals(0, new BigDecimal("7.180000").compareTo(p.path("actualRate").decimalValue()));
        JsonNode ap = bySource(poId, admin);
        assertEquals(3, ap.path("status").asInt());
        money("80.00", ap.path("exchangeDiff"));
    }

    @Test
    void backfillCreatesMissingPayables_onlyOnce() throws Exception {
        long sup = supplier("老供应商", null);
        JsonNode po = orderedPo(sup, "F-1", 1, "100", List.of(term(100, 1, 0)));
        long poId = po.path("id").asLong();
        jdbc.update("delete from fin_payable_installment where tenant_id = 0");
        jdbc.update("delete from fin_payable where tenant_id = 0");
        long forwarder = supplier("深圳捷运", null);
        jdbc.update("insert into forwarder_statement (tenant_id, forwarder_id, period, status, our_total, statement_total, confirmed_at) "
                + "values (0, ?, '2026-10', 2, 3200.00, 3250.00, now())", forwarder);
        long st = jdbc.queryForObject("select max(id) from forwarder_statement where tenant_id = 0", Long.class);

        backfill.run(null);
        backfill.run(null);
        assertEquals(2, jdbc.queryForObject("select count(*) from fin_payable where tenant_id = 0", Integer.class));
        money("100.00", bySource(poId, admin).path("amount"));
        JsonNode fwd = ok(call(get(AP + "/by-source").param("sourceType", "2").param("sourceId", String.valueOf(st)), admin));
        assertEquals(3, fwd.path("payeeType").asInt());
        assertEquals("深圳捷运", fwd.path("payeeName").asText());
        money("3250.00", fwd.path("amount"));
        assertEquals("货代对账 2026-10", fwd.path("sourceNo").asText());
        assertEquals(LocalDate.now().toString(), fwd.path("nextDueDate").asText());
    }

    @Test
    void shortNoResendReducesPayable_tenantIsolation() throws Exception {
        long sup = supplier("宁波伺服", null);
        JsonNode po = orderedPo(sup, "G-1", 10, "904", List.of(term(100, 3, 0)));
        long poId = po.path("id").asLong();
        long poItem = po.path("items").get(0).path("id").asLong();
        JsonNode s = ok(call(json(post(SHIP), write(Map.of("poId", poId, "carrier", "顺丰", "trackingNo", "SF55667788",
                "shipDate", LocalDate.now().toString(), "items", List.of(Map.of("poItemId", poItem, "quantity", 10))))), lin));
        long shipmentId = s.path("shipment").path("id").asLong();
        JsonNode form = ok(call(get(GR + "/shipments/" + shipmentId), admin));
        ok(call(json(post(GR), write(Map.of("shipmentId", shipmentId, "receivedDate", LocalDate.now().toString(),
                "items", List.of(Map.of("shipmentItemId", form.path("shipment").path("items").get(0).path("id").asLong(),
                        "receivedQty", 8, "qualifiedQty", 8, "defectiveQty", 0))))), admin));
        assertTrue(blank(bySource(poId, admin).path("installments").get(0).path("dueDate")), "少发未处理，还没全部入库");
        long diff = jdbc.queryForObject("select id from receiving_discrepancy where po_id = ? and type = 1", Long.class, poId);
        ok(call(json(post(DIFF + "/" + diff + "/handle"), write(Map.of("resolution", 2, "note", "不补了"))), lin));
        JsonNode ap = bySource(poId, admin);
        money("7232.00", ap.path("amount"));
        assertEquals(LocalDate.now().toString(), ap.path("installments").get(0).path("dueDate").asText(), "少发不补后视为全部入库");

        // 其他租户看不到
        String other = jwtUtils.generateToken(Map.of("tenantId", 1009), "it_sales_admin", 3600);
        redis.opsForValue().set(com.zhul.erp.common.constants.RedisKeyConstants.TOKEN_PREFIX + other, "1");
        assertEquals(0, ok(call(get(AP), other)).path("total").asInt());
        assertTrue(blank(ok(call(get(AP + "/by-source").param("sourceType", "1").param("sourceId", String.valueOf(poId)), other))));
    }
}
