package com.zhul.erp.modules.finance;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhul.erp.modules.sales.SalesContractSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** spec finance/fx-settlement 与 sales/payment-receipt（到账按估算汇率、已结汇不能作废） */
class FxSettlementContractTest extends SalesContractSupport {

    private static final String FX = "/api/v1/finance/fx-settlements";
    private static final String FIN = "/api/v1/finance/receipts";
    private static final String PI = "/api/v1/sales/pis";

    private int usdBank;
    private int cnyBank;

    @BeforeEach
    void banks() {
        bankAccount("USD");
        usdBank = jdbc.queryForObject("select min(id) from tenant_bank_account where tenant_id = 0 and currency_code = 'USD'", Integer.class);
        jdbc.update("insert into tenant_bank_account (tenant_id, currency_code, bank_name, account_name, account_no, is_default) "
                + "values (0, 'CNY', '招商银行', '基本户', '6225888866886688', 0)");
        cnyBank = jdbc.queryForObject("select max(id) from tenant_bank_account where tenant_id = 0", Integer.class);
    }

    private long unclaimed(String amount, int daysAgo, String payer) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("currencyCode", "USD");
        body.put("amount", amount);
        body.put("receiptDate", LocalDate.now().minusDays(daysAgo).toString());
        body.put("bankAccountId", usdBank);
        body.put("payer", payer);
        return ok(call(json(post(FIN + "/unclaimed"), write(body)), admin)).path("id").asLong();
    }

    private Map<String, Object> settleBody(String amount, String cny) {
        Map<String, Object> b = new HashMap<>();
        b.put("settleDate", LocalDate.now().toString());
        b.put("currencyCode", "USD");
        b.put("amount", amount);
        b.put("amountCny", cny);
        b.put("bankAccountId", cnyBank);
        b.put("idempotencyKey", UUID.randomUUID().toString());
        return b;
    }

    private JsonNode settle(String amount, String cny) throws Exception {
        return call(json(post(FX), write(settleBody(amount, cny))), admin);
    }

    private Map<String, Object> receipt(long id) {
        return jdbc.queryForMap("select net_amount_cny, amount_cny, exchange_rate, settled_amount, settle_status, rate_source "
                + "from payment_receipt where id = ?", id);
    }

    private static void eq(String expected, Object actual) {
        assertEquals(0, new java.math.BigDecimal(expected).compareTo((java.math.BigDecimal) actual), "期望 " + expected + "，实际 " + actual);
    }

    @Test
    void fifoAcrossReceipts_partial_estimate_overBalance_voidLatestOnly_receiptVoidGuard_idempotent() throws Exception {
        long a = unclaimed("1000", 10, "Hector Martinez");
        long b = unclaimed("2000", 7, "ACROBOT");
        long c = unclaimed("500", 3, "PT. Indo Mulia Indah");
        assertEquals(1, ((Number) receipt(a).get("settle_status")).intValue());
        eq("7150.00", receipt(a).get("net_amount_cny"));

        JsonNode pending = ok(call(get(FX + "/pending"), admin)).get(0);
        money("3500.00", pending.path("balance"));
        money("7.150000", pending.path("estimateRate"));
        assertEquals(3, pending.path("receipts").size());

        assertEquals("人民币到账不需要结汇", fail(call(json(post(FX), write(new HashMap<>(settleBody("100", "700")) {{
            put("currencyCode", "CNY");
        }})), admin)).path("message").asText());
        Map<String, Object> wrongBank = settleBody("100", "680");
        wrongBank.put("bankAccountId", usdBank);
        assertEquals("入账账户须为人民币账户", fail(call(json(post(FX), write(wrongBank)), admin)).path("message").asText());

        // 第一批：USD 2,500 → ¥17,000，汇率 6.8
        Map<String, Object> first = settleBody("2500", "17000");
        JsonNode s1 = ok(call(json(post(FX), write(first)), admin));
        assertEquals("JH" + TODAY + "001", s1.path("settleNo").asText());
        money("6.800000", s1.path("exchangeRate"));
        assertEquals(2, s1.path("allocations").size());
        assertEquals(s1.path("id").asLong(), ok(call(json(post(FX), write(first)), admin)).path("id").asLong(), "幂等");
        eq("6800.00", receipt(a).get("net_amount_cny"));
        assertEquals(3, ((Number) receipt(a).get("settle_status")).intValue());
        assertEquals(4, ((Number) receipt(a).get("rate_source")).intValue());
        // B 结了 1,500，剩 500 按最新汇率 6.8 估算
        eq("13600.00", receipt(b).get("net_amount_cny"));
        eq("1500.00", receipt(b).get("settled_amount"));
        assertEquals(2, ((Number) receipt(b).get("settle_status")).intValue());
        eq("3400.00", receipt(c).get("net_amount_cny"));
        assertEquals("结汇金额超过待结汇余额 USD 1,000.00", fail(settle("1200", "8000")).path("message").asText());

        // 第二批：USD 1,000 → ¥6,900，汇率 6.9；B 分两次结完，加权汇率 6.825
        JsonNode s2 = ok(settle("1000", "6900"));
        eq("13650.00", receipt(b).get("net_amount_cny"));
        eq("6.825000", receipt(b).get("exchange_rate"));
        assertEquals(3, ((Number) receipt(b).get("settle_status")).intValue());
        eq("3450.00", receipt(c).get("net_amount_cny"));
        assertEquals(0, ok(call(get(FX + "/pending"), admin)).size());

        // 已结汇的到账不能作废；只能作废最新的结汇单
        assertEquals("这笔到账已结汇（" + s1.path("settleNo").asText() + "），请先作废结汇单",
                fail(call(json(post(FIN + "/" + a + "/void"), write(Map.of("reason", "x"))), admin)).path("message").asText());
        assertEquals("请先作废之后的结汇单 " + s2.path("settleNo").asText(),
                fail(call(json(post(FX + "/" + s1.path("id").asLong() + "/void"), write(Map.of("reason", "x"))), admin)).path("message").asText());
        JsonNode voided = ok(call(json(post(FX + "/" + s2.path("id").asLong() + "/void"), write(Map.of("reason", "入账金额填错"))), admin));
        assertEquals(2, voided.path("status").asInt());
        eq("13600.00", receipt(b).get("net_amount_cny"));
        assertEquals(2, ((Number) receipt(b).get("settle_status")).intValue());
        eq("3400.00", receipt(c).get("net_amount_cny"));
        assertEquals(1, ((Number) receipt(c).get("settle_status")).intValue());
        money("1000.00", ok(call(get(FX + "/pending"), admin)).get(0).path("balance"));

        assertEquals(1, ok(call(get(FX), admin)).path("total").asInt(), "作废的默认不显示");
    }

    @Test
    void platformFeeConvertsAtSameRate_newReceiptUsesLatestRate() throws Exception {
        long customer = customer("Hoorain HTF", "India");
        long pi = sentPi(quotationItemIds(quotation(customer, 2, "USD", null, l("6ES7214-1AG40-0XB0", 1, "5000", "1000"))), null);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("paymentMethod", "ALIBABA_TA");
        body.put("platformOrderNo", "241009001234");
        body.put("amount", "1000");
        body.put("platformFee", "25");
        body.put("receiptDate", LocalDate.now().toString());
        ok(call(json(post(PI + "/" + pi + "/platform-receipts"), write(body)), admin));
        long id = jdbc.queryForObject("select id from payment_receipt where pi_id = ? and kind = 2", Long.class, pi);

        JsonNode s = ok(settle("975", "6630"));
        Map<String, Object> r = receipt(id);
        eq("6630.00", r.get("net_amount_cny"));
        eq("6800.00", r.get("amount_cny"));
        eq("6.800000", r.get("exchange_rate"));
        // 收款记录：结汇状态筛选与结汇单编号
        JsonNode done = ok(call(json(post(FIN + "/records"), write(Map.of("settleStatus", 3))), admin));
        assertEquals(1, done.path("total").asInt());
        assertEquals(s.path("settleNo").asText(), done.path("records").get(0).path("settleNos").get(0).asText());
        assertEquals(0, ok(call(json(post(FIN + "/records"), write(Map.of("settleStatus", 1))), admin)).path("total").asInt());

        // 之后新登记的外币到账按最近一次结汇汇率估算
        long later = unclaimed("100", 0, "Natty Jantima");
        eq("680.00", receipt(later).get("net_amount_cny"));
        assertEquals(3, ((Number) receipt(later).get("rate_source")).intValue());
    }
}
