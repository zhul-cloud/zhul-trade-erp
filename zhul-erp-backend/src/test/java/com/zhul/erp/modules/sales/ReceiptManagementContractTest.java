package com.zhul.erp.modules.sales;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** spec sales/payment-receipt：付款方式、实际入账人民币、平台收款、未认领到账与认领、收款记录统计 */
class ReceiptManagementContractTest extends SalesContractSupport {

    private static final String FIN = "/api/v1/finance/receipts";
    private static final int MENU_RECEIPTS = 100085;
    private static final int BTN_PLATFORM = 110183;
    private static final int BTN_CLAIM = 110184;

    /** 合计 USD 1,000.00 的已发送 PI */
    private long pi1000(String customer) throws Exception {
        long c = customer(customer, "India");
        return sentPi(quotationItemIds(quotation(c, 2, "USD", null, l("6ES7214-1AG40-0XB0", 1, "5000", "1000"))), null);
    }

    private int bank() {
        return jdbc.queryForObject("select min(id) from tenant_bank_account where tenant_id = 0", Integer.class);
    }

    private JsonNode platform(long piId, String method, String orderNo, String amount, String fee, String token) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("paymentMethod", method);
        body.put("platformOrderNo", orderNo);
        body.put("amount", amount);
        body.put("platformFee", fee);
        body.put("receiptDate", LocalDate.now().toString());
        return call(json(post(PI + "/" + piId + "/platform-receipts"), write(body)), token);
    }

    private JsonNode unclaimed(String currency, String amount, String payer) throws Exception {
        if (jdbc.queryForObject("select count(*) from tenant_bank_account where tenant_id = 0 and currency_code = ?", Integer.class, currency) == 0) {
            bankAccount(currency);
        }
        int bankId = jdbc.queryForObject("select min(id) from tenant_bank_account where tenant_id = 0 and currency_code = ?", Integer.class, currency);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("currencyCode", currency);
        body.put("amount", amount);
        body.put("receiptDate", LocalDate.now().toString());
        body.put("bankAccountId", bankId);
        body.put("payer", payer);
        return ok(call(json(post(FIN + "/unclaimed"), write(body)), admin));
    }

    private JsonNode lastReceipt(long piId) throws Exception {
        JsonNode list = ok(call(get(PI + "/" + piId), admin)).path("receipts");
        return list.get(list.size() - 1);
    }

    @Test
    void slipMethod_confirmWithActualCny_methodNameSnapshot() throws Exception {
        long id = pi1000("ACROBOT");
        JsonNode afterSlip = ok(call(multipart(PI + "/" + id + "/slips")
                .file(new MockMultipartFile("files", "slip.png", "image/png", PNG))
                .param("amount", "1000").param("paidDate", LocalDate.now().toString()).param("paymentMethod", "WECHAT"), admin));
        JsonNode slip = afterSlip.path("receipts").get(0);
        assertEquals("微信", slip.path("paymentMethodName").asText());
        assertEquals(1, slip.path("channel").asInt());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", "1000");
        body.put("receiptDate", LocalDate.now().toString());
        body.put("bankAccountId", bank());
        body.put("slipId", slip.path("id").asLong());
        JsonNode paid = ok(call(json(post(PI + "/" + id + "/receipts"), write(body)), admin));
        JsonNode r = paid.path("receipts").get(1);
        assertEquals("微信", r.path("paymentMethodName").asText(), "带出水单的付款方式");
        assertEquals(3, r.path("rateSource").asInt(), "外币到账未结汇，按估算汇率");
        assertEquals(1, r.path("settleStatus").asInt());
        money("7.150000", r.path("exchangeRate"));
        money("7150.00", r.path("netAmountCny"));
        money("7150.00", paid.path("netAmountCny"));
        money("1000", paid.path("receivedAmount"));

        // 改名后历史记录仍显示原名称
        jdbc.update("update dict_item set item_name = '微信支付' where dict_type = 'payment_method' and item_code = 'WECHAT' and tenant_id = 0");
        try {
            assertEquals("微信", lastReceipt(id).path("paymentMethodName").asText());
        } finally {
            jdbc.update("update dict_item set item_name = '微信' where dict_type = 'payment_method' and item_code = 'WECHAT' and tenant_id = 0");
        }
    }

    @Test
    void platformReceipt_grossCountsFeeSeparated_duplicateAndOfflineRejected_voidOwnOnly() throws Exception {
        long id = pi1000("Hoorain HTF");
        String piNo = jdbc.queryForObject("select pi_no from proforma_invoice where id = ?", String.class, id);
        JsonNode pi = ok(platform(id, "ALIBABA_TA", "241009001234", "1000", "25", admin));
        assertEquals(4, pi.path("receiptStatus").asInt(), "按客户付款金额计入，已到账");
        money("25", pi.path("platformFeeAmount"));
        JsonNode r = pi.path("receipts").get(0);
        assertEquals(2, r.path("channel").asInt());
        money("975", r.path("netAmount"));
        money("6971.25", r.path("netAmountCny"));

        long other = pi1000("Siam Automation");
        assertEquals("平台订单号 241009001234 已登记过（" + piNo + "）",
                fail(platform(other, "ALIBABA_TA", "241009001234", "100", "0", admin)).path("message").asText());
        assertEquals("平台收款只能选择线上付款方式", fail(platform(other, "TT", "X1", "100", "0", admin)).path("message").asText());
        assertEquals("平台手续费不能超过付款金额", fail(platform(other, "MIC", "M1", "100", "120", admin)).path("message").asText());

        // 业务员（没有登记到账权限）只能作废自己登记的平台收款
        loginWithResources("it_platform_rep", MENU_PI, BTN_SLIP, BTN_PLATFORM);
        String rep = token("it_platform_rep");
        long mine = ok(platform(other, "MIC", "MIC-001", "100", "2", rep)).path("receipts").get(0).path("id").asLong();
        jdbc.update("update payment_receipt set operator_id = ? where id = ?", BUYER_LIN, r.path("id").asLong());
        assertEquals("只能作废自己登记的平台收款", fail(call(json(post(PI + "/" + id + "/receipts/" + r.path("id").asLong() + "/void"),
                "{\"reason\":\"录错\"}"), rep)).path("message").asText());
        JsonNode voided = ok(call(json(post(PI + "/" + other + "/receipts/" + mine + "/void"), "{\"reason\":\"订单号录错\"}"), rep));
        assertEquals(1, voided.path("receiptStatus").asInt());
    }

    @Test
    void unclaimedThenClaim_sameCurrencyOnly_secondClaimRejected_unclaimAndVoid() throws Exception {
        long id = pi1000("ACROBOT");
        String piNo = jdbc.queryForObject("select pi_no from proforma_invoice where id = ?", String.class, id);
        long usd = unclaimed("USD", "667", "ACROBOT TECHNOLOGIES PVT LTD").path("id").asLong();
        long eur = unclaimed("EUR", "500", "NORDIC VALVE AB").path("id").asLong();
        assertEquals(2, ok(call(get(FIN + "/unclaimed"), admin)).path("unclaimed").size());

        loginWithResources("it_claim_rep", MENU_PI, BTN_SLIP, BTN_CLAIM);
        String rep = token("it_claim_rep");
        JsonNode claimable = ok(call(get(PI + "/" + id + "/claimable-receipts"), rep));
        assertEquals(1, claimable.size(), "只列同币种");
        assertEquals(usd, claimable.get(0).path("id").asLong());
        assertEquals("ACROBOT TECHNOLOGIES PVT LTD", claimable.get(0).path("payer").asText());
        assertEquals(403, perform(get(FIN + "/unclaimed"), rep).getStatus(), "业务员不能进收款单");

        JsonNode claimed = ok(call(json(post(PI + "/" + id + "/claim"), "{\"receiptId\":" + usd + "}"), rep));
        money("667", claimed.path("receivedAmount"));
        assertEquals(3, claimed.path("receiptStatus").asInt());
        JsonNode rec = claimed.path("receipts").get(claimed.path("receipts").size() - 1);
        assertTrue(rec.path("claimedByName").isTextual() || rec.path("claimedAt").isTextual());
        loginAsAdmin("it_sales_admin");
        admin = token("it_sales_admin");
        long id2 = pi1000("Delta Industrial");
        loginWithResources("it_claim_rep", MENU_PI, BTN_SLIP, BTN_CLAIM);
        rep = token("it_claim_rep");
        assertEquals("这笔到账已被 " + piNo + " 认领",
                fail(call(json(post(PI + "/" + id2 + "/claim"), "{\"receiptId\":" + usd + "}"), rep)).path("message").asText());
        assertTrue(fail(call(json(post(PI + "/" + id2 + "/claim"), "{\"receiptId\":" + eur + "}"), rep)).path("message").asText().contains("币种不同"));
        loginAsAdmin("it_sales_admin");
        admin = token("it_sales_admin");
        assertEquals(1, ok(call(get(FIN + "/unclaimed"), admin)).path("recentClaimed").size());

        // 认领错了：总经理取消认领，PI 收款重算
        assertEquals("请填写原因", fail(call(json(post(FIN + "/" + usd + "/unclaim"), "{\"reason\":\" \"}"), admin)).path("message").asText());
        ok(call(json(post(FIN + "/" + usd + "/unclaim"), "{\"reason\":\"认领错 PI\"}"), admin));
        JsonNode after = ok(call(get(PI + "/" + id), admin));
        money("0", after.path("receivedAmount"));
        assertEquals(1, after.path("receiptStatus").asInt());
        assertEquals(2, ok(call(get(FIN + "/unclaimed"), admin)).path("unclaimed").size());

        ok(call(json(post(FIN + "/" + eur + "/void"), "{\"reason\":\"重复登记\"}"), admin));
        assertEquals(1, ok(call(get(FIN + "/unclaimed"), admin)).path("unclaimed").size());
    }

    @Test
    void recordsSummary_matchesRows_byChannel() throws Exception {
        long a = pi1000("ACROBOT");
        long b = pi1000("Hoorain HTF");
        ok(platform(a, "ALIBABA_TA", "TA-1", "1000", "25", admin));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", "600");
        body.put("receiptDate", LocalDate.now().toString());
        body.put("bankAccountId", bank());
        ok(call(json(post(PI + "/" + b + "/receipts"), write(body)), admin));
        unclaimed("USD", "50", "UNKNOWN");

        JsonNode page = ok(call(json(post(FIN + "/records"), "{}"), admin));
        assertEquals(2, page.path("total").asInt(), "未认领到账不在收款记录里");
        JsonNode online = page.path("summary").get(0);
        JsonNode offline = page.path("summary").get(1);
        JsonNode total = page.path("summary").get(2);
        assertEquals(1, online.path("count").asInt());
        money("6971.25", online.path("netAmountCny"));
        money("25", online.path("fees").get(0).path("amount"));
        assertEquals(1, offline.path("count").asInt());
        money("4290", offline.path("netAmountCny"));
        BigDecimal sum = BigDecimal.ZERO;
        for (JsonNode r : page.path("records")) {
            sum = sum.add(r.path("netAmountCny").decimalValue());
        }
        money(sum.toPlainString(), total.path("netAmountCny"));
        assertEquals(1, ok(call(json(post(FIN + "/records"), "{\"channel\":2}"), admin)).path("total").asInt());
        assertEquals(1, ok(call(json(post(FIN + "/records"), "{\"keyword\":\"TA-1\"}"), admin)).path("total").asInt());
    }

    @Test
    void unclaimedFiles_upToFive_previewForClaimer_keptAfterClaim() throws Exception {
        long id = pi1000("ACROBOT");
        long usd = unclaimed("USD", "1000", "ACROBOT TECHNOLOGIES PVT LTD").path("id").asLong();
        var upload = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart(FIN + "/unclaimed/" + usd + "/files");
        for (int i = 0; i < 2; i++) {
            upload.file(new org.springframework.mock.web.MockMultipartFile("files", "回单" + i + ".png", "image/png", PNG));
        }
        ok(call(upload, admin));
        JsonNode row = ok(call(get(FIN + "/unclaimed"), admin)).path("unclaimed").get(0);
        assertEquals(2, row.path("fileNames").size());
        assertEquals("回单0.png", row.path("fileNames").get(0).asText());
        assertFalse(ok(call(get(FIN + "/" + usd + "/files/0/url"), admin)).path("url").asText().isEmpty());
        // 最多 5 个
        var tooMany = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart(FIN + "/unclaimed/" + usd + "/files");
        for (int i = 0; i < 4; i++) {
            tooMany.file(new org.springframework.mock.web.MockMultipartFile("files", "x" + i + ".png", "image/png", PNG));
        }
        fail(call(tooMany, admin));

        // 业务员认领前可以预览候选到账的附件，认领后附件保留
        loginWithResources("it_claim_rep", MENU_PI, BTN_SLIP, BTN_CLAIM);
        String rep = token("it_claim_rep");
        JsonNode cand = ok(call(get(PI + "/" + id + "/claimable-receipts"), rep));
        assertEquals(2, cand.get(0).path("fileNames").size());
        assertFalse(ok(call(get(PI + "/" + id + "/claimable-receipts/" + usd + "/files/1/url"), rep)).path("url").asText().isEmpty());
        ok(call(json(post(PI + "/" + id + "/claim"), "{\"receiptId\":" + usd + "}"), rep));
        fail(call(get(PI + "/" + id + "/claimable-receipts/" + usd + "/files/0/url"), rep));
        loginAsAdmin("it_sales_admin");
        admin = token("it_sales_admin");
        String keys = jdbc.queryForObject("select file_keys from payment_receipt where id = ?", String.class, usd);
        assertEquals(2, objectMapper.readTree(keys).size());
        // 认领后不能再补附件
        var late = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart(FIN + "/unclaimed/" + usd + "/files")
                .file(new org.springframework.mock.web.MockMultipartFile("files", "y.png", "image/png", PNG));
        fail(call(late, admin));
    }

    @Test
    void listsShowPrimaryContact_followsChange_emptyWhenNone() throws Exception {
        long id = pi1000("ACROBOT");
        long customer = jdbc.queryForObject("select customer_id from proforma_invoice where id = ?", Long.class, id);
        jdbc.update("update customer set contact_name = '' where id = ?", customer);
        JsonNode pis = ok(call(json(post(PI + "/page"), "{}"), admin));
        assertTrue(pis.path("records").get(0).path("customerContact").isMissingNode() || pis.path("records").get(0).path("customerContact").isNull()
                || pis.path("records").get(0).path("customerContact").asText().isEmpty(), "没有主联系人时为空");
        jdbc.update("update customer set contact_name = 'Rahul Mehta' where id = ?", customer);
        assertEquals("Rahul Mehta", ok(call(json(post(PI + "/page"), "{}"), admin)).path("records").get(0).path("customerContact").asText());
        // 换了主联系人，列表跟着变
        jdbc.update("update customer set contact_name = 'Priya Shah' where id = ?", customer);
        assertEquals("Priya Shah", ok(call(json(post(PI + "/page"), "{}"), admin)).path("records").get(0).path("customerContact").asText());
        ok(confirmReceipt(id, "1000", null, false, admin));
        JsonNode records = ok(call(json(post(FIN + "/records"), "{}"), admin));
        assertEquals("Priya Shah", records.path("records").get(0).path("customerContact").asText());
    }

    // ---------------------------------------------------------------- 收款单（change finance-cash-docs）

    @Test
    void receiptDoc_numberedInOrder_detailRemarkLogs_readOnlyAfterVoid_salesScopeReadOnly() throws Exception {
        long id = pi1000("ACROBOT");
        ok(confirmReceipt(id, "400", null, false, admin));
        long first = lastReceipt(id).path("id").asLong();
        ok(confirmReceipt(id, "300", null, false, admin));
        long second = lastReceipt(id).path("id").asLong();

        JsonNode d1 = ok(call(get(FIN + "/" + first), admin));
        JsonNode d2 = ok(call(get(FIN + "/" + second), admin));
        String no1 = d1.path("receipt").path("receiptNo").asText();
        String no2 = d2.path("receipt").path("receiptNo").asText();
        String today = LocalDate.now().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        assertTrue(no1.matches("SK" + today + "\\d{3}"), no1);
        assertEquals(Integer.parseInt(no1.substring(10)) + 1, Integer.parseInt(no2.substring(10)), "同一天连续编号");
        assertTrue(d1.path("editable").asBoolean());
        assertEquals("登记", d1.path("logs").get(0).path("action").asText());
        assertEquals("PI", d1.path("related").get(0).path("type").asText());
        // 关键词按收款单号查
        assertEquals(1, ok(call(json(post(FIN + "/records"), "{\"keyword\":\"" + no2 + "\"}"), admin)).path("total").asInt());

        JsonNode remarked = ok(call(json(post(FIN + "/" + first + "/remark"), "{\"remark\":\"客户分两笔付\"}"), admin));
        assertEquals("客户分两笔付", remarked.path("receipt").path("note").asText());
        assertTrue(remarked.path("logs").toString().contains("修改备注"));

        // 业务员：只读，且只看自己数据范围内 PI 的收款单
        jdbc.update("insert into role_resource (role_code, resource_id, resource_code) values (?, ?, '')", BUYER_ROLE, MENU_PI);
        String lin = token("it_lin");
        assertEquals("收款单不存在", fail(call(get(FIN + "/" + first), lin)).path("message").asText());
        jdbc.update("update proforma_invoice set owner_id = ? where id = ?", BUYER_LIN, id);
        JsonNode seen = ok(call(get(FIN + "/" + first), lin));
        assertFalse(seen.path("editable").asBoolean());
        assertEquals(403, perform(json(post(FIN + "/" + first + "/remark"), "{\"remark\":\"x\"}"), lin).getStatus());

        // 作废后只读
        ok(call(json(post(PI + "/" + id + "/receipts/" + first + "/void"), "{\"reason\":\"金额录错\"}"), admin));
        JsonNode voided = ok(call(get(FIN + "/" + first), admin));
        assertFalse(voided.path("editable").asBoolean());
        assertTrue(voided.path("logs").toString().contains("作废"));
        assertEquals("收款单已作废", fail(call(json(post(FIN + "/" + first + "/remark"), "{\"remark\":\"x\"}"), admin))
                .path("message").asText());
    }
}
