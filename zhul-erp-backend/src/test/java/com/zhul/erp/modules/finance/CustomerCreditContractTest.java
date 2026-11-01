package com.zhul.erp.modules.finance;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhul.erp.modules.sales.SalesContractSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 客户余额的端到端契约（spec finance/customer-credit、finance/customer-refund、sales/proforma-invoice、
 * sales/payment-receipt、finance/order-cost、finance/operating-report、finance/other-income）。
 * 汇率：系统汇率 USD 7.15，银行到账没有结汇单时按 7.15 估算。
 */
class CustomerCreditContractTest extends SalesContractSupport {

    private static final String CREDIT = "/api/v1/finance/credits";
    private static final String REFUND = "/api/v1/finance/refunds";
    private static final String INCOME = "/api/v1/finance/other-incomes";
    private static final String REPORT = "/api/v1/finance/operating-report";

    private long customer;

    @BeforeEach
    void setUp() {
        bankAccount("USD");
        customer = customer("ACROBOT Automacao", "Brazil");
    }

    // ---------------------------------------------------------------- 工具

    private long upload(String ownerType) throws Exception {
        return ok(call(multipart("/api/v1/attachments").file(new MockMultipartFile("file", "凭证.png", "image/png", PNG))
                .param("ownerType", ownerType), admin)).path("id").asLong();
    }

    /** 一张已发送的 PI：型号 1000.00，可选代收款与抵扣余额 */
    private long pi(String collection, String credit) throws Exception {
        long q = quotation(customer, 2, "USD", null, l("6ES7214-1AG40-0XB0", 1, "5000", "1000"));
        JsonNode pi = ok(createPi(quotationItemIds(q)));
        long id = pi.path("id").asLong();
        Map<String, Object> body = saveBody(pi);
        body.put("bankAccountId", jdbc.queryForObject("select min(id) from tenant_bank_account where tenant_id = 0", Integer.class));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fees = (List<Map<String, Object>>) body.get("fees");
        fees.clear();
        if (collection != null) {
            fees.add(new LinkedHashMap<>(Map.of("feeName", "Other Charges", "amount", collection, "feeType", 2)));
        }
        if (credit != null) {
            body.put("creditAmount", credit);
        }
        ok(savePi(id, body));
        ok(send(id));
        return id;
    }

    private JsonNode piDetail(long id) throws Exception {
        return ok(call(get(PI + "/" + id), admin));
    }

    private long convert(long pi) throws Exception {
        ok(call(json(post(PI + "/" + pi + "/convert"), "{}"), admin));
        return jdbc.queryForObject("select max(id) from sales_order where tenant_id = 0 and pi_id = ?", Long.class, pi);
    }

    private void platform(long pi, String amount, String fee) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("paymentMethod", "ALIBABA_TA");
        body.put("platformOrderNo", "TA" + System.nanoTime());
        body.put("amount", amount);
        body.put("platformFee", fee);
        body.put("receiptDate", LocalDate.now().toString());
        ok(call(json(post(PI + "/" + pi + "/platform-receipts"), write(body)), admin));
    }

    private JsonNode balances() throws Exception {
        return ok(call(get(CREDIT + "/customer/" + customer), admin));
    }

    private Map<String, Object> refund(long so, String amount, int method) throws Exception {
        Map<String, Object> b = new HashMap<>();
        b.put("soId", so);
        b.put("refundDate", LocalDate.now().toString());
        b.put("reason", "QUALITY");
        b.put("amount", amount);
        b.put("method", method);
        b.put("attachmentIds", method == 3 ? List.of() : List.of(upload("REFUND")));
        return b;
    }

    private Map<String, Object> withdraw(String amount, String actualCny) throws Exception {
        Map<String, Object> b = new HashMap<>();
        b.put("customerId", customer);
        b.put("currencyCode", "USD");
        b.put("amount", amount);
        b.put("withdrawDate", LocalDate.now().toString());
        b.put("method", 4);
        b.put("methodName", "支付宝");
        b.put("payeeAccount", "lucas@example.com");
        b.put("actualCny", actualCny);
        b.put("attachmentIds", List.of(upload("REFUND")));
        return b;
    }

    // ---------------------------------------------------------------- 用例

    @Test
    void afterSalesRefundToCredit_thenNextPiDeducts_andReleasesOnClose() throws Exception {
        // 银行收齐的订单：售后退款转客户余额，不要凭证，人民币按到账汇率锁定
        long pi1 = pi(null, null);
        confirmReceipt(pi1, "1000", null, false, admin);
        long so = convert(pi1);
        JsonNode info = ok(call(get(REFUND + "/refundable/" + so), admin));
        assertFalse(info.path("platformOnly").asBoolean());
        JsonNode tk = ok(call(json(post(REFUND), write(refund(so, "200", 3))), admin));
        money("1430.00", tk.path("amountCny"));
        assertEquals("转客户余额", tk.path("methodName").asText());
        JsonNode acc = balances().get(0);
        money("200", acc.path("available"));
        money("1430.00", acc.path("availableCny"));
        money("1430.00", ok(call(get(SO + "/" + so), admin)).path("cost").path("refund"));

        // 新 PI 抵扣 200：保存即占用，发送后计入已到账；超过可用余额被拒
        long q = quotation(customer, 2, "USD", null, l("6ES7214-1AG40-0XB0", 1, "5000", "500"));
        JsonNode draft = ok(createPi(quotationItemIds(q)));
        long pi2 = draft.path("id").asLong();
        Map<String, Object> body = saveBody(draft);
        body.put("bankAccountId", jdbc.queryForObject("select min(id) from tenant_bank_account where tenant_id = 0", Integer.class));
        ((List<?>) body.get("fees")).clear();
        body.put("creditAmount", "300");
        assertTrue(fail(savePi(pi2, body)).path("message").asText().startsWith("可用余额只有 USD 200.00"));
        body.put("creditAmount", "200");
        ok(savePi(pi2, body));
        money("0", balances().get(0).path("available"));
        JsonNode list = ok(call(get(CREDIT), admin)).path("records").get(0);
        money("200", list.path("occupied"));
        ok(send(pi2));
        JsonNode d = piDetail(pi2);
        assertEquals("部分到账", d.path("receiptStatusName").asText());
        money("200", d.path("credit").path("creditReceived"));
        money("300", d.path("version").path("amountDue"));
        // 结汇先进先出跳过余额抵扣：USD 1,000 只分摊到银行到账，余额抵扣的人民币价值不变（spec finance/fx-settlement）
        jdbc.update("insert into tenant_bank_account (tenant_id, currency_code, bank_name, account_name, account_no, is_default) "
                + "values (0, 'CNY', '招商银行', '基本户', '6225888866886688', 0)");
        Map<String, Object> fx = new HashMap<>();
        fx.put("settleDate", LocalDate.now().toString());
        fx.put("currencyCode", "USD");
        fx.put("amount", "1000");
        fx.put("amountCny", "7000");
        fx.put("bankAccountId", jdbc.queryForObject("select max(id) from tenant_bank_account where tenant_id = 0", Integer.class));
        fx.put("idempotencyKey", java.util.UUID.randomUUID().toString());
        ok(call(json(post("/api/v1/finance/fx-settlements"), write(fx)), admin));
        Map<String, Object> creditRow = jdbc.queryForMap(
                "select amount_cny, settle_status, settled_amount from payment_receipt where pi_id = ? and payment_method = 'CREDIT' and status = 1", pi2);
        assertEquals(0, new BigDecimal("1430.00").compareTo((BigDecimal) creditRow.get("amount_cny")));
        assertEquals(0, ((Number) creditRow.get("settle_status")).intValue());
        assertEquals(0, new BigDecimal("1000").compareTo(jdbc.queryForObject(
                "select settled_amount from payment_receipt where pi_id = ? and payment_method <> 'CREDIT' and status = 1", BigDecimal.class, pi1)));
        // 余额抵扣不能手动作废，PI 只有余额抵扣时可以关闭，关闭即释放
        long creditReceipt = jdbc.queryForObject("select id from payment_receipt where pi_id = ? and payment_method = 'CREDIT' and status = 1",
                Long.class, pi2);
        assertEquals("PI 上的余额抵扣随 PI 自动处理，请修改 PI 的抵扣金额", fail(call(json(post(PI + "/" + pi2 + "/receipts/" + creditReceipt + "/void"),
                "{\"reason\":\"x\"}"), admin)).path("message").asText());
        Map<String, Object> close = Map.of("reason", jdbc.queryForObject(
                "select item_code from dict_item where dict_type = 'quotation_lost_reason' and status = 1 limit 1", String.class),
                "note", "客户不要了", "markQuotationLost", false);
        ok(call(json(post(PI + "/" + pi2 + "/close"), write(close)), admin));
        money("200", balances().get(0).path("available"));
        assertEquals(0, jdbc.queryForObject("select count(*) from payment_receipt where pi_id = ? and payment_method = 'CREDIT' and status = 1",
                Integer.class, pi2));
        // 余额被占用时来源退款单不能作废；释放后可以
        ok(call(json(post(PI + "/" + pi2 + "/reopen"), "{}"), admin));
        money("0", balances().get(0).path("available"));
        assertTrue(fail(call(json(post(REFUND + "/" + tk.path("id").asLong() + "/void"), "{\"reason\":\"录错\"}"), admin))
                .path("message").asText().contains("FWPI"));
    }

    @Test
    void platformOrderRefundOnlyBackToPlatform() throws Exception {
        long pi = pi(null, null);
        platform(pi, "1000", "30");
        long so = convert(pi);
        assertTrue(ok(call(get(REFUND + "/refundable/" + so), admin)).path("platformOnly").asBoolean());
        assertEquals("平台收款的订单只能原路退回", fail(call(json(post(REFUND), write(refund(so, "50", 3))), admin)).path("message").asText());
        assertEquals("平台收款的订单只能原路退回", fail(call(json(post(REFUND), write(refund(so, "50", 2))), admin)).path("message").asText());
        ok(call(json(post(REFUND), write(refund(so, "50", 1))), admin));
    }

    @Test
    void collectionPaidByPlatform_transfersNetToCredit_withdrawRecordsFxGain_notRevenue() throws Exception {
        long pi = pi("300", null);
        JsonNode v = piDetail(pi).path("version");
        money("1300", v.path("totalAmount"));
        money("300", v.path("collectionAmount"));
        // 部分到账不转入
        platform(pi, "650", "19.50");
        assertTrue(balances().isEmpty());
        assertEquals("待收齐", piDetail(pi).path("credit").path("collectionTransferred").asBoolean() ? "已转入" : "待收齐");
        // 收齐：代收承担手续费 = 39 × 300 ÷ 1300 = 9.00，净额 291.00 转入
        platform(pi, "650", "19.50");
        JsonNode c = piDetail(pi).path("credit");
        assertTrue(c.path("collectionTransferred").asBoolean());
        money("9.00", c.path("collectionFee"));
        money("291.00", balances().get(0).path("available"));
        money("2080.65", balances().get(0).path("availableCny"));
        // 订单收入不含代收，手续费只算货款部分
        long so = convert(pi);
        JsonNode cost = ok(call(get(SO + "/" + so), admin)).path("cost");
        money("7150.00", cost.path("revenue"));
        money("2080.65", cost.path("collectionCny"));
        // 手续费 = Σ(毛额人民币 − 实收人民币) − 代收承担 9 × 7.15；每笔实收人民币各自 HALF_UP 两位（630.50 × 7.15 = 4,508.08），故为 214.49
        money("214.49", cost.path("receiptFee"));
        // 转给客户：实际转出 ¥2,050.00，汇兑收益 ¥30.65 记其他收入；余额提现不进经营统计的退款
        JsonNode tk = ok(call(json(post(CREDIT + "/withdraw"), write(withdraw("291", "2050"))), admin));
        assertEquals(2, tk.path("refundType").asInt());
        assertEquals("支付宝", tk.path("methodName").asText());
        money("0", balances().get(0).path("available"));
        JsonNode income = ok(call(get(INCOME).param("category", "FX_GAIN"), admin)).path("records").get(0);
        money("30.65", income.path("amount"));
        assertEquals("REFUND", income.path("sourceType").asText());
        assertEquals("汇兑损益由余额提现自动生成，请作废来源退款单",
                fail(call(json(post(INCOME + "/" + income.path("id").asLong() + "/void"), "{\"reason\":\"x\"}"), admin)).path("message").asText());
        JsonNode totals = ok(call(get(REFUND + "/totals"), admin));
        money("0", totals.path("afterSales"));
        money("2050.00", totals.path("withdraw"));
        JsonNode month = ok(call(get(REPORT).param("year", String.valueOf(LocalDate.now().getYear())), admin))
                .path("rows").get(LocalDate.now().getMonthValue() - 1);
        money("0", month.path("refund"));
        // 代收已转给客户：作废到账被拒
        long receipt = jdbc.queryForObject("select max(id) from payment_receipt where pi_id = ? and payment_method = 'ALIBABA_TA'", Long.class, pi);
        assertTrue(fail(call(json(post(PI + "/" + pi + "/receipts/" + receipt + "/void"), "{\"reason\":\"x\"}"), admin))
                .path("message").asText().startsWith("代收已转给客户，不能作废这笔到账"));
        // 作废提现：余额恢复、汇兑损益一起作废
        ok(call(json(post(REFUND + "/" + tk.path("id").asLong() + "/void"), "{\"reason\":\"转错账户\"}"), admin));
        money("291", balances().get(0).path("available"));
        assertTrue(ok(call(get(INCOME).param("category", "FX_GAIN"), admin)).path("records").isEmpty());
    }

    @Test
    void overpaidIsNotRevenue_refundToCustomerInOneStep_reconciliationBalances() throws Exception {
        long pi = pi(null, null);
        confirmReceipt(pi, "1050", null, false, admin);
        long so = convert(pi);
        money("7150.00", ok(call(get(SO + "/" + so), admin)).path("cost").path("revenue"));
        JsonNode row = ok(call(get(CREDIT + "/overpaid"), admin)).get(0);
        money("50", row.path("pending"));
        money("357.50", row.path("pendingCny"));
        Map<String, Object> req = new HashMap<>();
        req.put("ownerType", "PI");
        req.put("ownerId", pi);
        req.put("amount", "60");
        req.put("mode", "CREDIT");
        assertEquals("待处理多收只有 USD 50.00", fail(call(json(post(CREDIT + "/overpaid"), write(req)), admin)).path("message").asText());
        req.put("amount", "50");
        req.put("mode", "REFUND");
        req.put("withdrawDate", LocalDate.now().toString());
        req.put("method", 4);
        req.put("methodName", "支付宝");
        req.put("actualCny", "350");
        req.put("attachmentIds", List.of(upload("REFUND")));
        assertTrue(ok(call(json(post(CREDIT + "/overpaid"), write(req)), admin)).isEmpty());
        money("0", balances().get(0).path("available"));
        money("7150.00", ok(call(get(SO + "/" + so), admin)).path("cost").path("revenue"));
        JsonNode recon = ok(call(get(CREDIT + "/reconciliation").param("month", LocalDate.now().toString().substring(0, 7))
                .param("currencyCode", "USD"), admin));
        assertTrue(recon.path("balanced").asBoolean());
        money("50", recon.path("total").path("overpaidIn").path("amount"));
        money("50", recon.path("total").path("withdraw").path("amount"));
        money("0", recon.path("total").path("closing").path("amount"));
        money("7.50", ok(call(get(INCOME).param("category", "FX_GAIN"), admin)).path("records").get(0).path("amount"));
    }

    @Test
    void manualOrderUsesCredit_voidAndCancelRelease_withdrawLossIsNegativeIncome() throws Exception {
        Map<String, Object> adj = new HashMap<>();
        adj.put("customerId", customer);
        adj.put("currencyCode", "USD");
        adj.put("amount", "300");
        adj.put("amountCny", "2130");
        adj.put("reason", "旧 ERP 余额迁移");
        ok(call(json(post(CREDIT + "/adjust"), write(adj)), admin));
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("model", "6ES7214-1AG40-0XB0");
        line.put("quantity", 1);
        line.put("unitPrice", "250");
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("customerId", customer);
        b.put("currencyCode", "USD");
        b.put("items", List.of(line));
        long so = ok(call(json(post(SO), write(b)), admin)).path("id").asLong();
        assertEquals("抵扣不能超过待付金额 USD 250.00",
                fail(call(json(post(SO + "/" + so + "/credit"), "{\"amount\":260}"), admin)).path("message").asText());
        JsonNode order = ok(call(json(post(SO + "/" + so + "/credit"), "{\"amount\":120}"), admin));
        assertEquals("部分到账", order.path("receiptStatusName").asText());
        money("180", order.path("credit").path("available"));
        // 作废抵扣：余额释放
        long receipt = jdbc.queryForObject("select id from payment_receipt where so_id = ? and payment_method = 'CREDIT' and status = 1", Long.class, so);
        ok(call(json(post(SO + "/" + so + "/receipts/" + receipt + "/void"), "{\"reason\":\"抵错了\"}"), admin));
        money("300", balances().get(0).path("available"));
        // 再抵扣后取消订单：抵扣作废、余额释放
        ok(call(json(post(SO + "/" + so + "/credit"), "{\"amount\":250}"), admin));
        money("50", balances().get(0).path("available"));
        ok(call(json(post(SO + "/" + so + "/cancel"), write(Map.of("reason", "客户取消"))), admin));
        money("300", balances().get(0).path("available"));
        // 实际转出多于锁定价值：汇兑损失记为负数其他收入；相等时不生成
        ok(call(json(post(CREDIT + "/withdraw"), write(withdraw("100", "720"))), admin));
        money("-10.00", ok(call(get(INCOME).param("category", "FX_GAIN"), admin)).path("records").get(0).path("amount"));
        ok(call(json(post(CREDIT + "/withdraw"), write(withdraw("100", "710"))), admin));
        assertEquals(1, ok(call(get(INCOME).param("category", "FX_GAIN"), admin)).path("records").size());
        money("100", balances().get(0).path("available"));
    }

    @Test
    void manualAdjust_requiresReason_negativeLimitedToAvailable() throws Exception {
        Map<String, Object> b = new HashMap<>();
        b.put("customerId", customer);
        b.put("currencyCode", "USD");
        b.put("amount", "500");
        b.put("amountCny", "3550");
        b.put("reason", "");
        assertEquals("请填写调整原因", fail(call(json(post(CREDIT + "/adjust"), write(b)), admin)).path("message").asText());
        b.put("reason", "旧 ERP 余额迁移");
        JsonNode flows = ok(call(json(post(CREDIT + "/adjust"), write(b)), admin));
        assertEquals("手工调整", flows.get(0).path("typeName").asText());
        b.put("amount", "-600");
        assertEquals("最多能减少 USD 500.00", fail(call(json(post(CREDIT + "/adjust"), write(b)), admin)).path("message").asText());
        b.put("amount", "-100");
        ok(call(json(post(CREDIT + "/adjust"), write(b)), admin));
        money("400", balances().get(0).path("available"));
        money("2840.00", balances().get(0).path("availableCny"));
        // 手动登记其他收入不能选汇兑损益
        Map<String, Object> inc = new HashMap<>();
        inc.put("incomeDate", LocalDate.now().toString());
        inc.put("category", "FX_GAIN");
        inc.put("amount", "1");
        inc.put("remark", "x");
        assertEquals("汇兑损益由余额提现自动生成，不能手动登记", fail(call(json(post(INCOME), write(inc)), admin)).path("message").asText());
        JsonNode summary = ok(call(get(CREDIT + "/summary"), admin));
        money("400", summary.path("currencies").get(0).path("amount"));
        assertEquals(new BigDecimal("400").compareTo(summary.path("currencies").get(0).path("amount").decimalValue()), 0);
    }
}
