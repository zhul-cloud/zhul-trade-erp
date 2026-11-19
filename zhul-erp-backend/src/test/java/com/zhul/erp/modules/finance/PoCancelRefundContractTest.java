package com.zhul.erp.modules.finance;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhul.erp.modules.sales.SalesContractSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * openspec po-cancel-refund：已付款的采购订单取消时选是否退款，按付款方式自动收尾（finance/accounts-payable
 * 「采购订单取消后的收尾」、purchase/purchase-order「确认下单与修改」）；待报销项撤回（finance/reimbursement「撤回待报销项」）。
 */
class PoCancelRefundContractTest extends SalesContractSupport {

    private static final String BX = "/api/v1/finance/reimbursements";
    private static final String AP = "/api/v1/finance/payables";
    private static final String PY = "/api/v1/finance/payments";
    private static final int MENU_PO = 100088;
    private static final int MENU_BX = 100105;
    private static final int[] BUYER_BUTTONS = {110189, 110190, 110191, 110192, 110205};

    private String lin;
    private String jiang;
    private long account;

    @BeforeEach
    void people() {
        for (int id : new int[] {MENU_PO, MENU_BX, 100089}) {
            jdbc.update("insert into role_resource (role_code, resource_id, resource_code) values (?, ?, '')", BUYER_ROLE, id);
        }
        for (int id : BUYER_BUTTONS) {
            jdbc.update("insert into role_resource (role_code, resource_id, resource_code) values (?, ?, '')", BUYER_ROLE, id);
        }
        lin = token("it_lin");
        jiang = token("it_jiang");
        bankAccount("USD");
        jdbc.update("insert into tenant_bank_account (tenant_id, currency_code, bank_name, account_name, account_no, is_default) "
                + "values (0, 'CNY', '招商银行', '基本户', '6225888866886688', 0)");
        account = jdbc.queryForObject("select max(id) from tenant_bank_account where tenant_id = 0", Long.class);
    }

    // ---------------------------------------------------------------- 准备数据

    private JsonNode orderedPo(long supplierId, String model, String price) throws Exception {
        long c = customer("ACROBOT", "India");
        List<Long> items = quotationItemIds(quotation(c, 2, "USD", null, l(model, 1, "100", "200")));
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
        body.put("paymentTerms", List.of(Map.of("percent", 100, "trigger", 1, "days", 0)));
        ok(savePo(draft, body, admin));
        return ok(confirmPo(draft, admin));
    }

    private long upload(String ownerType, String token) throws Exception {
        return ok(call(multipart("/api/v1/attachments").file(new MockMultipartFile("file", "截图.png", "image/png", PNG))
                .param("ownerType", ownerType), token)).path("id").asLong();
    }

    private long payableOf(long poId) {
        return jdbc.queryForObject("select id from fin_payable where source_type = 1 and source_id = ?", Long.class, poId);
    }

    /** 付款：method 2 由林熙垫付，method 1 由财务对公付 */
    private long pay(int method, Map<Long, String> allocations) throws Exception {
        String token = method == 2 ? lin : admin;
        Map<String, Object> b = new HashMap<>();
        b.put("method", method);
        if (method == 1) {
            b.put("bankAccountId", account);
        }
        b.put("payDate", LocalDate.now().toString());
        BigDecimal total = BigDecimal.ZERO;
        List<Map<String, Object>> lines = new ArrayList<>();
        for (Map.Entry<Long, String> e : allocations.entrySet()) {
            lines.add(Map.of("payableId", e.getKey(), "amount", new BigDecimal(e.getValue())));
            total = total.add(new BigDecimal(e.getValue()));
        }
        b.put("amount", total);
        b.put("attachmentIds", List.of(upload("PAYMENT", token)));
        b.put("allocations", lines);
        b.put("idempotencyKey", UUID.randomUUID().toString());
        return ok(call(json(post(PY), write(b)), token)).path("id").asLong();
    }

    private JsonNode cancel(long poId, Boolean refund) throws Exception {
        Map<String, Object> b = new HashMap<>();
        b.put("reason", "客户要换型号");
        if (refund != null) {
            b.put("refund", refund);
        }
        return call(json(post(PO + "/" + poId + "/cancel"), write(b)), admin);
    }

    private JsonNode ap(long apId) throws Exception {
        return ok(call(get(AP + "/" + apId), admin));
    }

    private boolean voided(long paymentId) {
        return jdbc.queryForObject("select void_at is not null from fin_payment where id = ?", Boolean.class, paymentId);
    }

    /** 接口不输出空字段 */
    private static boolean blank(JsonNode n) {
        return n.isMissingNode() || n.isNull();
    }

    private static JsonNode item(JsonNode items, int type, long sourceId) {
        for (JsonNode i : items) {
            if (i.path("sourceType").asInt() == type && i.path("sourceId").asLong() == sourceId) {
                return i;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- 取消收尾

    @Test
    void advancePending_refund_voidsAdvance() throws Exception {
        long poId = orderedPo(supplier("上海成安机电", null), "C-1", "700").path("id").asLong();
        long apId = payableOf(poId);
        long py = pay(2, Map.of(apId, "700"));

        JsonNode preview = ok(call(get(PO + "/" + poId + "/cancel-preview"), admin));
        assertEquals(1, preview.size());
        assertEquals(1, preview.get(0).path("action").asInt(), "垫付还没报销：作废");

        assertEquals("请选择店铺 / 供应商是否退款", fail(cancel(poId, null)).path("message").asText());
        assertEquals(2, jdbc.queryForObject("select status from purchase_order where id = ?", Integer.class, poId), "没取消");

        JsonNode po = ok(cancel(poId, true));
        assertEquals(1, po.path("cancelRefund").asInt());
        assertTrue(voided(py));
        JsonNode a = ap(apId);
        money("0.00", a.path("paidAmount"));
        money("0.00", a.path("creditAmount"));
        assertEquals(4, a.path("status").asInt());
        assertNull(item(ok(call(get(BX + "/pending"), lin)), 1, py), "不再出现在待报销");
    }

    @Test
    void advanceClaimed_refund_registersReceivedForAdvancer() throws Exception {
        long poId = orderedPo(supplier("苏州欧姆龙", null), "C-2", "860").path("id").asLong();
        long apId = payableOf(poId);
        long py = pay(2, Map.of(apId, "860"));
        ok(call(json(post(BX), write(Map.of("items", List.of(Map.of("sourceType", 1, "sourceId", py))))), lin));

        assertEquals(2, ok(call(get(PO + "/" + poId + "/cancel-preview"), admin)).get(0).path("action").asInt());
        ok(cancel(poId, true));

        assertFalse(voided(py));
        money("0.00", ap(apId).path("creditAmount"));
        Long refund = jdbc.queryForObject("select id from fin_payment where kind = 2 and advance_user = 'it_lin' "
                + "and void_at is null and remark like '%自动登记%'", Long.class);
        JsonNode neg = item(ok(call(get(BX + "/pending"), lin)), 5, refund);
        assertNotNull(neg, "林熙待报销多一项负数");
        money("-860.00", neg.path("amount"));
    }

    @Test
    void companyPayment_refund_marksRefundExpected_untilRefunded() throws Exception {
        long poId = orderedPo(supplier("深圳工控", null), "C-3", "500").path("id").asLong();
        long apId = payableOf(poId);
        long py = pay(1, Map.of(apId, "500"));
        ok(cancel(poId, true));

        assertFalse(voided(py));
        JsonNode a = ap(apId);
        money("500.00", a.path("creditAmount"));
        assertFalse(blank(a.path("refundExpectedAt")));
        JsonNode list = ok(call(get(AP).param("refundExpected", "true").param("showClosed", "true"), admin));
        assertEquals(1, list.path("total").asInt());
        assertEquals(1, ok(call(get(AP + "/stats"), admin)).path("refundExpectedCount").asInt());

        Map<String, Object> refund = new HashMap<>();
        refund.put("payableId", apId);
        refund.put("amount", 500);
        refund.put("refundDate", LocalDate.now().toString());
        refund.put("bankAccountId", account);
        refund.put("attachmentIds", List.of(upload("PAYMENT", admin)));
        refund.put("idempotencyKey", UUID.randomUUID().toString());
        ok(call(json(post(PY + "/refunds"), write(refund)), admin));
        assertTrue(blank(ap(apId).path("refundExpectedAt")), "欠款为 0 后标记去掉");
        assertEquals(0, ok(call(get(AP).param("refundExpected", "true").param("showClosed", "true"), admin)).path("total").asInt());
    }

    @Test
    void noRefund_keepsCredit_andAdvanceStaysPending() throws Exception {
        long poId = orderedPo(supplier("淘宝·成安", null), "C-4", "700").path("id").asLong();
        long apId = payableOf(poId);
        long py = pay(2, Map.of(apId, "700"));
        JsonNode po = ok(cancel(poId, false));

        assertEquals(2, po.path("cancelRefund").asInt());
        assertFalse(voided(py));
        JsonNode a = ap(apId);
        money("700.00", a.path("creditAmount"));
        assertTrue(blank(a.path("refundExpectedAt")));
        assertNotNull(item(ok(call(get(BX + "/pending"), lin)), 1, py));
    }

    @Test
    void oneAdvanceForTwoOrders_refund_registersOnlyThisOrdersShare() throws Exception {
        long sup = supplier("华强电子", null);
        long poA = orderedPo(sup, "C-5", "700").path("id").asLong();
        long poB = orderedPo(sup, "C-6", "300").path("id").asLong();
        long apA = payableOf(poA);
        long apB = payableOf(poB);
        Map<Long, String> alloc = new java.util.LinkedHashMap<>();
        alloc.put(apA, "700");
        alloc.put(apB, "300");
        long py = pay(2, alloc);
        ok(cancel(poA, true));

        assertFalse(voided(py), "核销了两张，不作废");
        money("0.00", ap(apA).path("creditAmount"));
        money("300.00", ap(apB).path("paidAmount"));
        BigDecimal received = jdbc.queryForObject("select amount from fin_payment where kind = 2 and advance_user = 'it_lin' "
                + "and void_at is null", BigDecimal.class);
        assertEquals(0, new BigDecimal("700.00").compareTo(received));
    }

    @Test
    void unpaidOrder_cancelNeedsNoRefundChoice() throws Exception {
        long poId = orderedPo(supplier("没付款的店", null), "C-7", "100").path("id").asLong();
        assertEquals(0, ok(call(get(PO + "/" + poId + "/cancel-preview"), admin)).size());
        assertEquals(0, ok(cancel(poId, null)).path("cancelRefund").asInt());
    }

    // ---------------------------------------------------------------- 撤回待报销项

    @Test
    void withdrawPendingItems() throws Exception {
        long poId = orderedPo(supplier("工控优选店", null), "W-1", "860").path("id").asLong();
        long apId = payableOf(poId);
        long py = pay(2, Map.of(apId, "860"));
        JsonNode mine = item(ok(call(get(BX + "/pending"), lin)), 1, py);
        assertTrue(mine.path("withdrawable").asBoolean());

        // 别人不能撤回
        assertEquals("只能撤回自己的待报销项", fail(call(json(post(BX + "/items/withdraw"),
                write(Map.of("sourceType", 1, "sourceId", py, "reason", "x"))), jiang)).path("message").asText());
        // 快递费不在这里撤回
        jdbc.update("insert into courier_waybill (tenant_id, carrier, tracking_no, sent_date, freight, payer_id, forwarder_id, status) "
                + "values (0, '顺丰', 'SF1234', ?, 12.00, ?, 0, 1)", LocalDate.now(), BUYER_LIN);
        long wb = jdbc.queryForObject("select max(id) from courier_waybill where tenant_id = 0", Long.class);
        assertFalse(item(ok(call(get(BX + "/pending"), lin)), 2, wb).path("withdrawable").asBoolean());
        assertEquals("快递费随快递单处理", fail(call(json(post(BX + "/items/withdraw"),
                write(Map.of("sourceType", 2, "sourceId", wb, "reason", "x"))), lin)).path("message").asText());

        ok(call(json(post(BX + "/items/withdraw"), write(Map.of("sourceType", 1, "sourceId", py, "reason", "采购已取消，店铺已退款"))), lin));
        assertTrue(voided(py));
        money("0.00", ap(apId).path("paidAmount"));
        assertNull(item(ok(call(get(BX + "/pending"), lin)), 1, py));
        assertEquals("从待报销撤回：采购已取消，店铺已退款",
                jdbc.queryForObject("select void_reason from fin_payment where id = ?", String.class, py));
    }

    @Test
    void withdrawClaimedItem_rejected() throws Exception {
        long poId = orderedPo(supplier("已报销的店", null), "W-2", "300").path("id").asLong();
        long py = pay(2, Map.of(payableOf(poId), "300"));
        String bxNo = ok(call(json(post(BX), write(Map.of("items", List.of(Map.of("sourceType", 1, "sourceId", py))))), lin))
                .get(0).path("reimburseNo").asText();
        assertEquals("这一项已在报销单 " + bxNo + " 中，请先撤回报销单", fail(call(json(post(BX + "/items/withdraw"),
                write(Map.of("sourceType", 1, "sourceId", py, "reason", "x"))), lin)).path("message").asText());
        assertFalse(voided(py));
    }
}
