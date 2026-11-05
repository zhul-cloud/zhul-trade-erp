package com.zhul.erp.modules.finance;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhul.erp.modules.finance.constants.FinanceConstants;
import com.zhul.erp.modules.sales.SalesContractSupport;
import com.zhul.erp.modules.system.constants.DocumentType;
import com.zhul.erp.modules.system.service.DocumentNumberService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static com.zhul.erp.support.TestDates.today;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * spec finance/reimbursement、finance/expense，与 finance/accounts-payable（费用单生成应付、退款退到员工）、
 * purchase/receiving-discrepancy（退货运费垫付人）。
 */
class ReimbursementContractTest extends SalesContractSupport {

    private static final String BX = "/api/v1/finance/reimbursements";
    private static final String FE = "/api/v1/finance/expenses";
    private static final String AP = "/api/v1/finance/payables";
    private static final String PY = "/api/v1/finance/payments";
    private static final int MENU_PO = 100088;
    private static final int MENU_BX = 100105;
    private static final int[] BUYER_BUTTONS = {110189, 110190, 110191, 110192, 110205};

    @SpyBean
    private DocumentNumberService numbers;

    private String lin;
    private String jiang;
    private long account;
    private long personalAccount;

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
        jdbc.update("insert into tenant_bank_account (tenant_id, account_type, currency_code, bank_name, account_name, account_no, is_default) "
                + "values (0, 2, 'CNY', '招商银行', '张总', '6225888866881234', 0)");
        personalAccount = jdbc.queryForObject("select max(id) from tenant_bank_account where tenant_id = 0", Long.class);
    }

    // ---------------------------------------------------------------- 准备数据

    private JsonNode orderedPo(long supplierId, String model, int qty, String price) throws Exception {
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

    /** 林熙垫付这张采购订单的全款 */
    private JsonNode advance(long apId, String amount) throws Exception {
        Map<String, Object> b = new HashMap<>();
        b.put("method", 2);
        b.put("payDate", LocalDate.now().toString());
        b.put("amount", new BigDecimal(amount));
        b.put("attachmentIds", List.of(upload("PAYMENT", lin)));
        b.put("allocations", List.of(Map.of("payableId", apId, "amount", new BigDecimal(amount))));
        b.put("idempotencyKey", UUID.randomUUID().toString());
        return ok(call(json(post(PY), write(b)), lin));
    }

    private long courier(long payer, String freight) {
        jdbc.update("insert into courier_waybill (tenant_id, carrier, tracking_no, sent_date, freight, payer_id, forwarder_id, status) "
                + "values (0, '顺丰', ?, ?, ?, ?, 0, 1)", "SF" + System.nanoTime(), LocalDate.now(), new BigDecimal(freight), payer);
        return jdbc.queryForObject("select max(id) from courier_waybill where tenant_id = 0", Long.class);
    }

    private long returnFreight(long poId, long payer, String freight) {
        jdbc.update("insert into receiving_discrepancy (tenant_id, receipt_id, receipt_item_id, po_id, po_item_id, purchaser_id, model, type, "
                + "quantity, status, resolution, return_freight, return_payer_id, handled_by, handled_at) "
                + "values (0, 0, 0, ?, 0, ?, 'A-1', 2, 2, 2, 4, ?, ?, ?, now())",
                poId, payer, new BigDecimal(freight), payer, payer);
        return jdbc.queryForObject("select max(id) from receiving_discrepancy where tenant_id = 0", Long.class);
    }

    private static List<String> types(JsonNode items) {
        List<String> out = new ArrayList<>();
        items.forEach(i -> out.add(i.path("sourceType").asInt() + ":"
                + i.path("amount").decimalValue().setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()));
        return out;
    }

    private JsonNode submit(String token, JsonNode... items) throws Exception {
        List<Map<String, Object>> list = new ArrayList<>();
        for (JsonNode i : items) {
            list.add(Map.of("sourceType", i.path("sourceType").asInt(), "sourceId", i.path("sourceId").asLong()));
        }
        return call(json(post(BX), write(Map.of("items", list))), token);
    }

    private static JsonNode find(JsonNode items, int type) {
        for (JsonNode i : items) {
            if (i.path("sourceType").asInt() == type) {
                return i;
            }
        }
        throw new AssertionError("没有来源 " + type + "：" + items);
    }

    // ---------------------------------------------------------------- 待报销与提交

    @Test
    void pending_proof_submit_duplicate_withdraw_guards() throws Exception {
        long sup = supplier("淘宝店转供应商", null);
        JsonNode po = orderedPo(sup, "A-1", 1, "860");
        long apId = payableOf(po.path("id").asLong());
        JsonNode pay = advance(apId, "860.00");
        long wb = courier(BUYER_LIN, "80.00");
        courier(BUYER_JIANG, "50.00");
        long diff = returnFreight(po.path("id").asLong(), BUYER_LIN, "18.00");

        JsonNode pending = ok(call(get(BX + "/pending"), lin));
        assertEquals(3, pending.size(), pending.toString());
        assertTrue(types(pending).containsAll(List.of("1:860.00", "2:80.00", "3:18.00")));
        assertFalse(find(pending, 1).path("proofs").isEmpty(), "采购货款带付款截图");
        assertTrue(find(pending, 2).path("proofUploadable").asBoolean());
        assertEquals(1, ok(call(get(BX + "/pending"), jiang)).size(), "只看本人的");

        // 快递没截图不能提交；补传后提交
        JsonNode noProof = submit(lin, find(pending, 1), find(pending, 2));
        assertTrue(fail(noProof).path("message").asText().contains("没有截图，请补传"), noProof.toString());
        ok(call(json(post(BX + "/proofs"), write(Map.of("sourceType", 2, "sourceId", wb,
                "attachmentIds", List.of(upload("COURIER_PROOF", lin))))), lin));
        ok(call(json(post(BX + "/proofs"), write(Map.of("sourceType", 3, "sourceId", diff,
                "attachmentIds", List.of(upload("RETURN_PROOF", lin))))), lin));
        pending = ok(call(get(BX + "/pending"), lin));
        JsonNode bx = ok(submit(lin, find(pending, 1), find(pending, 2), find(pending, 3))).get(0);
        assertEquals("BX" + today() + "001", bx.path("reimburseNo").asText());
        money("958.00", bx.path("totalAmount"));
        assertEquals(1, bx.path("status").asInt());
        assertEquals(0, ok(call(get(BX + "/pending"), lin)).size());
        assertEquals(2, jdbc.queryForObject("select reimburse_status from fin_payment where id = ?", Integer.class, pay.path("id").asLong()));

        // 同一项不能再提交；已进报销单的付款不能作废、退货运费不能重新打开
        assertTrue(fail(submit(lin, find(pending, 1))).path("message").asText().contains("请刷新"));
        assertEquals("这笔垫付已进入报销单，不能作废", fail(call(json(post(PY + "/" + pay.path("id").asLong() + "/void"),
                write(Map.of("reason", "x"))), lin)).path("message").asText());
        assertTrue(fail(call(json(post("/api/v1/purchase/discrepancies/" + diff + "/reopen"), "{}"), lin)).path("message").asText()
                .contains("已在报销单 " + bx.path("reimburseNo").asText()));

        // 江晓晞不能撤回林熙的；林熙撤回后回到待报销
        long id = bx.path("id").asLong();
        assertFalse(call(json(post(BX + "/" + id + "/withdraw"), write(Map.of("reason", "x"))), jiang).path("code").asInt() == 0);
        ok(call(json(post(BX + "/" + id + "/withdraw"), write(Map.of("reason", "漏了一笔"))), lin));
        assertEquals(3, ok(call(get(BX + "/pending"), lin)).size());
        assertEquals(1, jdbc.queryForObject("select reimburse_status from fin_payment where id = ?", Integer.class, pay.path("id").asLong()));
        // 撤回后可以再提交（唯一位已释放）
        pending = ok(call(get(BX + "/pending"), lin));
        ok(submit(lin, find(pending, 2)));
    }

    @Test
    void adminSeesEveryone_submitsOnBehalf_splitByPerson() throws Exception {
        long linWb = courier(BUYER_LIN, "80.00");
        long jiangWb = courier(BUYER_JIANG, "50.00");
        for (long[] w : new long[][] {{linWb}, {jiangWb}}) {
            // 有「报销付款」权限的人可以给别人补传截图
            ok(call(json(post(BX + "/proofs"), write(Map.of("sourceType", 2, "sourceId", w[0],
                    "attachmentIds", List.of(upload("COURIER_PROOF", admin))))), admin));
        }
        // 没有权限的人带 all=true 仍只看本人的
        JsonNode linAll = ok(call(get(BX + "/pending").param("all", "true"), lin));
        assertEquals(1, linAll.size(), linAll.toString());
        assertTrue(linAll.get(0).path("mine").asBoolean());
        assertEquals(0, ok(call(get(BX + "/pending"), admin)).size(), "默认只看本人的");

        JsonNode all = ok(call(get(BX + "/pending").param("all", "true"), admin));
        assertEquals(2, all.size(), all.toString());
        Map<Long, JsonNode> byUser = new HashMap<>();
        all.forEach(i -> byUser.put(i.path("advanceUserId").asLong(), i));
        assertEquals(Set.of(BUYER_LIN, BUYER_JIANG), byUser.keySet());
        assertFalse(byUser.get(BUYER_LIN).path("mine").asBoolean());
        assertFalse(byUser.get(BUYER_LIN).path("advanceUserName").asText().isEmpty());

        // 代提交：两人的垫付各生成一张报销单，报销人是垫付本人
        JsonNode bxs = ok(submit(admin, byUser.get(BUYER_LIN), byUser.get(BUYER_JIANG)));
        assertEquals(2, bxs.size(), bxs.toString());
        Map<Long, JsonNode> bxByApplicant = new HashMap<>();
        bxs.forEach(b -> bxByApplicant.put(b.path("applicantId").asLong(), b));
        money("80.00", bxByApplicant.get(BUYER_LIN).path("totalAmount"));
        money("50.00", bxByApplicant.get(BUYER_JIANG).path("totalAmount"));
        assertEquals(1, ok(call(get(BX), lin)).path("total").asInt(), "报销人本人能看到");
        assertTrue(bxByApplicant.get(BUYER_LIN).path("canWithdraw").asBoolean(), "代提交的人能撤回");

        // 报销人本人和代提交的人都能撤回，别人不能
        long linBx = bxByApplicant.get(BUYER_LIN).path("id").asLong();
        long jiangBx = bxByApplicant.get(BUYER_JIANG).path("id").asLong();
        assertEquals("只有报销人或提交人可以撤回", fail(call(json(post(BX + "/" + linBx + "/withdraw"),
                write(Map.of("reason", "x"))), jiang)).path("message").asText());
        ok(call(json(post(BX + "/" + linBx + "/withdraw"), write(Map.of("reason", "代提交错了"))), admin));
        ok(call(json(post(BX + "/" + jiangBx + "/withdraw"), write(Map.of("reason", "漏了一笔"))), jiang));
        assertEquals(2, ok(call(get(BX + "/pending").param("all", "true"), admin)).size());
    }

    @Test
    void financePays_unpays_scope_and_paymentStats() throws Exception {
        long wb = courier(BUYER_LIN, "80.00");
        ok(call(json(post(BX + "/proofs"), write(Map.of("sourceType", 2, "sourceId", wb,
                "attachmentIds", List.of(upload("COURIER_PROOF", lin))))), lin));
        JsonNode bx = ok(submit(lin, find(ok(call(get(BX + "/pending"), lin)), 2))).get(0);
        long id = bx.path("id").asLong();
        courier(BUYER_JIANG, "50.00");

        // 采购员没有报销付款权限，只看本人的
        assertFalse(call(json(post(BX + "/" + id + "/pay"), write(Map.of("bankAccountId", account,
                "payDate", LocalDate.now().toString(), "attachmentIds", List.of(upload("REIMBURSE", lin))))), lin).path("code").asInt() == 0);
        assertEquals(1, ok(call(get(BX), lin)).path("total").asInt());
        assertEquals(0, ok(call(get(BX), jiang)).path("total").asInt());
        money("80.00", ok(call(get(BX + "/pending-total"), admin)));

        JsonNode paid = ok(call(json(post(BX + "/" + id + "/pay"), write(Map.of("bankAccountId", account,
                "payDate", LocalDate.now().toString(), "attachmentIds", List.of(upload("REIMBURSE", admin))))), admin));
        assertEquals(2, paid.path("status").asInt());
        assertFalse(paid.path("paymentNo").asText().isEmpty());
        assertEquals("已报销，不能撤回", fail(call(json(post(BX + "/" + id + "/withdraw"), write(Map.of("reason", "x"))), lin))
                .path("message").asText());
        JsonNode stats = ok(call(get(PY + "/stats"), admin));
        money("80.00", stats.path("reimburse"));
        money("0.00", stats.path("total"));
        JsonNode reimbursePay = ok(call(get(PY).param("kind", "3"), admin)).path("records").get(0);
        assertEquals(5, reimbursePay.path("payeeType").asInt());
        assertFalse(reimbursePay.path("canVoid").asBoolean(), "报销付款在报销单上撤销");

        JsonNode back = ok(call(json(post(BX + "/" + id + "/unpay"), write(Map.of("reason", "账户选错"))), admin));
        assertEquals(1, back.path("status").asInt());
        money("0.00", ok(call(get(PY + "/stats"), admin)).path("reimburse"));

        // 私对私：法人个人账户付给员工，只能选对私账户
        assertEquals("私对私请选择对私账户", fail(call(json(post(BX + "/" + id + "/pay"), write(Map.of("method", 3, "bankAccountId", account,
                "payDate", LocalDate.now().toString(), "attachmentIds", List.of(upload("REIMBURSE", admin))))), admin))
                .path("message").asText());
        assertEquals("公司对公请选择对公账户", fail(call(json(post(BX + "/" + id + "/pay"), write(Map.of("method", 1,
                "bankAccountId", personalAccount, "payDate", LocalDate.now().toString(),
                "attachmentIds", List.of(upload("REIMBURSE", admin))))), admin)).path("message").asText());
        JsonNode personal = ok(call(json(post(BX + "/" + id + "/pay"), write(Map.of("method", 3, "bankAccountId", personalAccount,
                "payDate", LocalDate.now().toString(), "attachmentIds", List.of(upload("REIMBURSE", admin))))), admin));
        assertEquals(2, personal.path("status").asInt());
        assertEquals(3, personal.path("payMethod").asInt());
        assertEquals("招商银行 · 张总 ****1234", personal.path("payPayerAccount").asText());
        JsonNode py = ok(call(get(PY).param("kind", "3"), admin)).path("records").get(0);
        assertEquals(3, py.path("method").asInt());
        assertEquals("招商银行 · 张总 ****1234", py.path("payerAccount").asText());
    }

    /** 某人登记一笔快递费垫付、补截图并提交成一张报销单 */
    private long submittedBx(long buyer, String token, String freight) throws Exception {
        long wb = courier(buyer, freight);
        ok(call(json(post(BX + "/proofs"), write(Map.of("sourceType", 2, "sourceId", wb,
                "attachmentIds", List.of(upload("COURIER_PROOF", token))))), token));
        JsonNode item = null;
        for (JsonNode i : ok(call(get(BX + "/pending"), token))) {
            if (i.path("sourceType").asInt() == 2 && i.path("sourceId").asLong() == wb) {
                item = i;
            }
        }
        return ok(submit(token, item)).get(0).path("id").asLong();
    }

    @Test
    void batchPay_eachGetsOwnPayment_sharedProof_allOrNothing() throws Exception {
        long a = submittedBx(BUYER_LIN, lin, "300.00");
        long b = submittedBx(BUYER_LIN, lin, "120.00");
        long c = submittedBx(BUYER_JIANG, jiang, "80.00");
        Map<String, Object> body = new HashMap<>();
        body.put("ids", List.of(c, a, b, a));
        body.put("bankAccountId", account);
        body.put("payDate", LocalDate.now().toString());
        body.put("attachmentIds", List.of(upload("REIMBURSE", admin)));
        body.put("remark", "11 月第一批");

        // 没有报销付款权限
        assertFalse(call(json(post(BX + "/batch-pay"), write(body)), lin).path("code").asInt() == 0);

        JsonNode done = ok(call(json(post(BX + "/batch-pay"), write(body)), admin));
        assertEquals(3, done.size(), "重复的 ID 只算一次");
        Set<String> nos = new HashSet<>();
        for (long id : new long[] {a, b, c}) {
            JsonNode d = ok(call(get(BX + "/" + id), admin));
            assertEquals(2, d.path("status").asInt());
            assertEquals(1, d.path("payProofs").size(), "每张报销单都能看到截图");
            assertEquals("11 月第一批", d.path("payRemark").asText());
            nos.add(d.path("paymentNo").asText());
        }
        assertEquals(3, nos.size(), "每张报销单各一张付款单");
        money("500.00", ok(call(get(PY + "/stats"), admin)).path("reimburse"));
        // 仍按单张撤销已付，不影响其他两张
        assertEquals(1, ok(call(json(post(BX + "/" + b + "/unpay"), write(Map.of("reason", "金额核对"))), admin)).path("status").asInt());
        assertEquals(1, ok(call(get(BX + "/" + a), admin)).path("payProofs").size());

        // 其中一张已撤回：整批不登记
        long d = submittedBx(BUYER_LIN, lin, "45.00");
        long e = submittedBx(BUYER_LIN, lin, "15.00");
        ok(call(json(post(BX + "/" + e + "/withdraw"), write(Map.of("reason", "填错"))), lin));
        String eNo = ok(call(get(BX + "/" + e), admin)).path("reimburseNo").asText();
        body.put("ids", List.of(d, e));
        body.put("attachmentIds", List.of(upload("REIMBURSE", admin)));
        assertEquals(eNo + " 已不是待付款，请刷新后重新勾选", fail(call(json(post(BX + "/batch-pay"), write(body)), admin))
                .path("message").asText());
        assertEquals(1, ok(call(get(BX + "/" + d), admin)).path("status").asInt(), "整批回滚");

        List<Long> many = new ArrayList<>();
        for (long i = 1; i <= 51; i++) {
            many.add(i);
        }
        body.put("ids", many);
        assertEquals("一次可登记 1 到 50 张报销单", fail(call(json(post(BX + "/batch-pay"), write(body)), admin)).path("message").asText());
    }

    @Test
    void refundReceivedByEmployee_reminder_onlyOwnAdvance() throws Exception {
        long sup = supplier("线上店铺甲", null);
        JsonNode po = orderedPo(sup, "B-1", 1, "860");
        long poId = po.path("id").asLong();
        long apId = payableOf(poId);
        advance(apId, "860.00");
        ok(call(json(post(PO + "/" + poId + "/cancel"), "{\"reason\":\"缺货\"}"), admin));
        money("860.00", ok(call(get(AP + "/by-source").param("sourceType", "1").param("sourceId", String.valueOf(poId)), admin))
                .path("creditAmount"));

        // 刚取消不提醒；超过 7 天提醒
        assertEquals(0, ok(call(get(BX + "/reminders"), lin)).size());
        jdbc.update("update fin_payable set update_time = date_sub(now(), interval 11 day) where id = ?", apId);
        JsonNode rem = ok(call(get(BX + "/reminders"), lin));
        assertEquals(1, rem.size());
        assertEquals(11, rem.get(0).path("days").asInt());
        assertEquals(0, ok(call(get(BX + "/reminders"), jiang)).size());

        Map<String, Object> body = new HashMap<>();
        body.put("payableId", apId);
        body.put("amount", 860);
        body.put("receivedDate", LocalDate.now().toString());
        body.put("attachmentIds", List.of(upload("PAYMENT", jiang)));
        body.put("idempotencyKey", UUID.randomUUID().toString());
        assertEquals("只能对你垫付过的应付单登记已收到退款", fail(call(json(post(BX + "/refunds"), write(body)), jiang)).path("message").asText());
        body.put("attachmentIds", List.of(upload("PAYMENT", lin)));
        body.put("idempotencyKey", UUID.randomUUID().toString());
        ok(call(json(post(BX + "/refunds"), write(body)), lin));
        money("0.00", ok(call(get(AP + "/by-source").param("sourceType", "1").param("sourceId", String.valueOf(poId)), admin))
                .path("creditAmount"));
        JsonNode pending = ok(call(get(BX + "/pending"), lin));
        assertTrue(types(pending).contains("5:-860.00"), pending.toString());
        assertTrue(types(pending).contains("1:860.00"));
        assertEquals("报销合计须大于 0", fail(submit(lin, find(pending, 5))).path("message").asText());
        assertEquals(0, ok(call(get(BX + "/reminders"), lin)).size());
    }

    @Test
    void expense_company_advance_allocation_void_locked_filter() throws Exception {
        long c = customer("Siam", "Thailand");
        List<Long> qi = quotationItemIds(quotation(c, 2, "USD", null, l("A-1", 2, "100", "200"), l("C-1", 1, "100", "200")));
        long pi = sentPi(qi, null);
        ok(uploadSlip(pi, "1.00", "2026-10-07", admin));
        JsonNode so = ok(call(json(post(PI + "/" + pi + "/convert"), "{}"), admin));
        long soId = so.path("id").isMissingNode() ? jdbc.queryForObject("select max(id) from sales_order where tenant_id = 0", Long.class)
                : so.path("id").asLong();
        List<Long> soItems = jdbc.queryForList("select id from sales_order_item where so_id = ? order by line_no", Long.class, soId);
        String soNo = jdbc.queryForObject("select so_no from sales_order where id = ?", String.class, soId);

        Map<String, Object> body = new HashMap<>();
        body.put("name", "补发运费");
        body.put("occurDate", LocalDate.now().toString());
        body.put("payeeType", 4);
        body.put("payeeName", "顺丰");
        body.put("currencyCode", "CNY");
        body.put("amount", 120);
        body.put("method", 1);
        body.put("allocations", List.of(Map.of("soItemId", soItems.get(0), "amount", 80), Map.of("soItemId", soItems.get(1), "amount", 30)));
        assertEquals("分摊合计须等于费用金额", fail(call(json(post(FE), write(body)), admin)).path("message").asText());
        body.put("allocations", List.of(Map.of("soItemId", soItems.get(0), "amount", 80), Map.of("soItemId", soItems.get(1), "amount", 40)));
        JsonNode fe = ok(call(json(post(FE), write(body)), admin));
        assertEquals("FE" + today() + "001", fe.path("expenseNo").asText());
        assertEquals(2, fe.path("allocations").size());
        JsonNode ap = ok(call(get(AP + "/by-source").param("sourceType", "3").param("sourceId", fe.path("id").asText()), admin));
        assertEquals(4, ap.path("payeeType").asInt());
        assertEquals("顺丰", ap.path("payeeName").asText());
        money("120.00", ap.path("amount"));
        assertEquals(1, ok(call(get(FE).param("soNo", soNo), admin)).path("total").asInt());
        money("120.00", ok(call(get(FE + "/month-total"), admin)));

        // 员工垫付的费用进垫付人的待报销
        Map<String, Object> adv = new HashMap<>(body);
        adv.put("name", "样品快递费");
        adv.put("amount", 35);
        adv.put("method", 2);
        adv.put("advanceUserId", BUYER_LIN);
        adv.put("allocations", List.of());
        assertEquals("员工垫付的费用请上传凭证", fail(call(json(post(FE), write(adv)), admin)).path("message").asText());
        adv.put("attachmentIds", List.of(upload("EXPENSE", admin)));
        JsonNode fe2 = ok(call(json(post(FE), write(adv)), admin));
        assertTrue(types(ok(call(get(BX + "/pending"), lin))).contains("4:35.00"));
        assertEquals(0, jdbc.queryForObject("select count(*) from fin_payable where source_type = 3 and source_id = ?", Integer.class,
                fe2.path("id").asLong()));

        // 公司对公的应付付款后不能改金额、不能作废；没付款的可以作废，应付关闭
        // 应付用私对私付款：要填付款个人账户，单独统计
        Map<String, Object> pay = new HashMap<>();
        pay.put("method", 3);
        pay.put("payDate", LocalDate.now().toString());
        pay.put("amount", 120);
        pay.put("attachmentIds", List.of(upload("PAYMENT", admin)));
        pay.put("allocations", List.of(Map.of("payableId", ap.path("id").asLong(), "amount", 120)));
        pay.put("idempotencyKey", UUID.randomUUID().toString());
        assertEquals("请选择付款账户", fail(call(json(post(PY), write(pay)), admin)).path("message").asText());
        pay.put("bankAccountId", personalAccount);
        pay.put("idempotencyKey", UUID.randomUUID().toString());
        JsonNode privatePay = ok(call(json(post(PY), write(pay)), admin));
        assertEquals(3, privatePay.path("method").asInt());
        money("120.00", ok(call(get(PY + "/stats"), admin)).path("personal"));
        Map<String, Object> change = new HashMap<>(body);
        change.put("amount", 150);
        change.put("allocations", List.of());
        assertEquals("已经付款，不能修改金额与收款方",
                fail(call(json(put(FE + "/" + fe.path("id").asLong()), write(change)), admin)).path("message").asText());
        assertEquals("已经付款，不能作废", fail(call(json(post(FE + "/" + fe.path("id").asLong() + "/void"),
                write(Map.of("reason", "x"))), admin)).path("message").asText());
        Map<String, Object> rename = new HashMap<>(body);
        rename.put("name", "补发运费（客户少收）");
        assertEquals("补发运费（客户少收）", ok(call(json(put(FE + "/" + fe.path("id").asLong()), write(rename)), admin)).path("name").asText());

        Map<String, Object> third = new HashMap<>(body);
        third.put("allocations", List.of());
        JsonNode fe3 = ok(call(json(post(FE), write(third)), admin));
        ok(call(json(post(FE + "/" + fe3.path("id").asLong() + "/void"), write(Map.of("reason", "录错"))), admin));
        assertEquals(4, ok(call(get(AP + "/by-source").param("sourceType", "3").param("sourceId", fe3.path("id").asText()), admin))
                .path("status").asInt());
        assertEquals(2, ok(call(get(FE), admin)).path("total").asInt(), "作废的默认不显示");

        // 采购员没有费用单菜单
        assertFalse(call(get(FE), lin).path("code").asInt() == 0);
    }

    @Test
    void handlingReturnFreightDefaultsPayerToHandler() throws Exception {
        long sup = supplier("宁波伺服", null);
        JsonNode po = orderedPo(sup, "G-1", 5, "100");
        long poId = po.path("id").asLong();
        long poItem = po.path("items").get(0).path("id").asLong();
        JsonNode s = ok(call(json(post("/api/v1/purchase/shipments"), write(Map.of("poId", poId, "carrier", "顺丰", "trackingNo", "SF55667788",
                "shipDate", LocalDate.now().toString(), "items", List.of(Map.of("poItemId", poItem, "quantity", 5))))), lin));
        long shipmentId = s.path("shipment").path("id").asLong();
        JsonNode form = ok(call(get("/api/v1/warehouse/receipts/shipments/" + shipmentId), admin));
        ok(call(json(post("/api/v1/warehouse/receipts"), write(Map.of("shipmentId", shipmentId, "receivedDate", LocalDate.now().toString(),
                "items", List.of(Map.of("shipmentItemId", form.path("shipment").path("items").get(0).path("id").asLong(),
                        "receivedQty", 5, "qualifiedQty", 3, "defectiveQty", 2))))), admin));
        long diff = jdbc.queryForObject("select id from receiving_discrepancy where po_id = ? and type = 2", Long.class, poId);
        JsonNode handled = ok(call(json(post("/api/v1/purchase/discrepancies/" + diff + "/handle"), write(Map.of("resolution", 4,
                "returnCarrier", "顺丰", "returnTrackingNo", "SF1188776655", "returnFreight", 18, "note", "外壳破损"))), lin));
        assertEquals(BUYER_LIN, handled.path("returnPayerId").asLong());
        assertTrue(types(ok(call(get(BX + "/pending"), lin))).contains("3:18.00"));
        // 重新打开后不再是待报销
        ok(call(json(post("/api/v1/purchase/discrepancies/" + diff + "/reopen"), "{}"), lin));
        assertFalse(types(ok(call(get(BX + "/pending"), lin))).contains("3:18.00"));
    }

    // ---------------------------------------------------------------- 提交与来源作废交错（audit BUS-011）

    /**
     * 林熙提交报销时停在取报销编号处（已读到待报销项、还没写明细），同时执行 undo（作废 / 撤销 / 重新打开来源）。
     * 两者只能成功一个：提交成功时 undo 必须被拒绝，提示里含 rejected。
     */
    private void raceSubmitWith(List<Integer> types, Callable<JsonNode> undo, String rejected) throws Exception {
        CountDownLatch read = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        AtomicReference<Thread> submitter = new AtomicReference<>();
        doAnswer(inv -> {
            if (Thread.currentThread() == submitter.get()) {
                read.countDown();
                resume.await(10, TimeUnit.SECONDS);
            }
            return inv.callRealMethod();
        }).when(numbers).next(eq(DocumentType.BX));
        JsonNode pending = ok(call(get(BX + "/pending"), lin));
        JsonNode[] chosen = types.stream().map(t -> find(pending, t)).toArray(JsonNode[]::new);

        CompletableFuture<JsonNode> submit = CompletableFuture.supplyAsync(() -> {
            submitter.set(Thread.currentThread());
            return unchecked(() -> submit(lin, chosen));
        });
        assertTrue(read.await(10, TimeUnit.SECONDS), "提交应已读到待报销项");
        CompletableFuture<JsonNode> other = CompletableFuture.supplyAsync(() -> unchecked(undo));
        try {
            other.get(1500, TimeUnit.MILLISECONDS);
        } catch (TimeoutException waiting) {
            // 修复后来源操作在等提交释放行锁，放行提交后再看结果
        }
        resume.countDown();
        ok(submit.get(15, TimeUnit.SECONDS));
        JsonNode r = other.get(15, TimeUnit.SECONDS);
        assertTrue(r.path("code").asInt() != 0, "来源已进报销单，操作应被拒绝：" + r);
        assertTrue(r.path("message").asText().contains(rejected), r.toString());
    }

    private static JsonNode unchecked(Callable<JsonNode> c) {
        try {
            return c.call();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void race_advancePaymentVoid() throws Exception {
        JsonNode po = orderedPo(supplier("垫付竞态", null), "R-1", 1, "300");
        long pay = advance(payableOf(po.path("id").asLong()), "300.00").path("id").asLong();
        raceSubmitWith(List.of(FinanceConstants.ITEM_PAYMENT),
                () -> call(json(post(PY + "/" + pay + "/void"), write(Map.of("reason", "x"))), admin),
                "这笔垫付已进入报销单，不能作废");
        assertNull(jdbc.queryForObject("select void_at from fin_payment where id = ?", java.time.LocalDateTime.class, pay));
    }

    @Test
    void race_receivedRefundVoid() throws Exception {
        long wb = courier(BUYER_LIN, "80.00");
        ok(call(json(post(BX + "/proofs"), write(Map.of("sourceType", 2, "sourceId", wb,
                "attachmentIds", List.of(upload("COURIER_PROOF", lin))))), lin));
        jdbc.update("insert into fin_payment (tenant_id, payment_no, kind, payee_type, payee_name, method, pay_date, amount, amount_cny, "
                + "advance_user, create_by) values (0, 'ITPY-RF', ?, 1, '退款供应商', ?, ?, 20.00, 20.00, 'it_lin', 'it_lin')",
                FinanceConstants.KIND_REFUND, FinanceConstants.METHOD_ADVANCE, LocalDate.now());
        long refund = jdbc.queryForObject("select id from fin_payment where payment_no = 'ITPY-RF'", Long.class);
        jdbc.update("insert into biz_attachment (tenant_id, owner_type, owner_id, kind, storage, file_key, url, file_name, file_size, "
                + "content_type, processing_status, uploaded_by) values (0, 'PAYMENT', ?, 1, 'LOCAL', 'it/bx/rf.png', '', 'rf.png', 10, "
                + "'image/png', 0, ?)", refund, BUYER_LIN);
        raceSubmitWith(List.of(FinanceConstants.ITEM_COURIER, FinanceConstants.ITEM_REFUND),
                () -> call(json(post(PY + "/" + refund + "/void"), write(Map.of("reason", "x"))), admin),
                "这笔垫付已进入报销单，不能作废");
        assertNull(jdbc.queryForObject("select void_at from fin_payment where id = ?", java.time.LocalDateTime.class, refund));
    }

    @Test
    void race_courierUndoHandOver() throws Exception {
        long wb = courier(BUYER_LIN, "80.00");
        ok(call(json(post(BX + "/proofs"), write(Map.of("sourceType", 2, "sourceId", wb,
                "attachmentIds", List.of(upload("COURIER_PROOF", lin))))), lin));
        jdbc.update("insert into outbound_order (tenant_id, ob_no, source, status, courier_waybill_id) values (0, 'ITOB-RACE', 1, 3, ?)", wb);
        long ob = jdbc.queryForObject("select id from outbound_order where ob_no = 'ITOB-RACE'", Long.class);
        raceSubmitWith(List.of(FinanceConstants.ITEM_COURIER),
                () -> call(json(post("/api/v1/logistics/outbounds/" + ob + "/undo-hand-over"), write(Map.of("reason", "x"))), admin),
                "这票快递的运费已在报销单");
        assertEquals(1, jdbc.queryForObject("select status from courier_waybill where id = ?", Integer.class, wb), "快递仍有效");
        assertEquals(3, jdbc.queryForObject("select status from outbound_order where id = ?", Integer.class, ob), "出库单仍是已交货代");
    }

    @Test
    void race_returnFreightReopen() throws Exception {
        JsonNode po = orderedPo(supplier("退货运费竞态", null), "R-2", 1, "300");
        long diff = returnFreight(po.path("id").asLong(), BUYER_LIN, "18.00");
        ok(call(json(post(BX + "/proofs"), write(Map.of("sourceType", 3, "sourceId", diff,
                "attachmentIds", List.of(upload("RETURN_PROOF", lin))))), lin));
        raceSubmitWith(List.of(FinanceConstants.ITEM_RETURN_FREIGHT),
                () -> call(json(post("/api/v1/purchase/discrepancies/" + diff + "/reopen"), "{}"), admin),
                "这笔退货运费已在报销单");
        assertEquals(2, jdbc.queryForObject("select status from receiving_discrepancy where id = ?", Integer.class, diff), "差异仍是已处理");
    }
}
