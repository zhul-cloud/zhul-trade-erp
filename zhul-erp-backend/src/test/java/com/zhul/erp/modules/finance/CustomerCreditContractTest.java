package com.zhul.erp.modules.finance;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.finance.constants.FinanceConstants;
import com.zhul.erp.modules.finance.service.CustomerCreditLedger;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

    @Autowired
    private CustomerCreditLedger ledger;

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
    void overpaidFromPlatform_onlyBackToPlatform_offlineCannot() throws Exception {
        long pi = pi(null, null);
        platform(pi, "1050", "0");
        convert(pi);
        JsonNode row = ok(call(get(CREDIT + "/overpaid"), admin)).get(0);
        money("50", row.path("pending"));
        JsonNode orig = row.path("platformReceipts");
        assertEquals(1, orig.size(), row.toString());
        String orderNo = orig.get(0).path("platformOrderNo").asText();
        assertFalse(orig.get(0).path("platform").asText().isEmpty());

        Map<String, Object> req = new HashMap<>();
        req.put("ownerType", "PI");
        req.put("ownerId", pi);
        req.put("amount", "50");
        req.put("mode", "REFUND");
        req.put("withdrawDate", LocalDate.now().toString());
        req.put("method", 2);
        req.put("actualCny", "355");
        req.put("attachmentIds", List.of(upload("REFUND")));
        assertEquals("这张单有平台收款，只能原路退回平台", fail(call(json(post(CREDIT + "/overpaid"), write(req)), admin))
                .path("message").asText());
        req.put("method", 1);
        assertTrue(ok(call(json(post(CREDIT + "/overpaid"), write(req)), admin)).isEmpty());
        JsonNode tk = ok(call(get(REFUND).param("refundType", "2"), admin)).path("records").get(0);
        assertEquals(1, tk.path("method").asInt(), tk.toString());
        assertEquals("原路退回平台（" + orig.get(0).path("platform").asText() + "）", tk.path("methodName").asText());
        assertEquals(orderNo, tk.path("payeeAccount").asText());
        money("0", balances().get(0).path("available"));
        // 汇兑损益 = 账面价值 357.50 − 实际 355.00；作废退款单后余额恢复、汇兑损益作废
        money("2.50", ok(call(get(INCOME).param("category", "FX_GAIN"), admin)).path("records").get(0).path("amount"));
        ok(call(json(post(REFUND + "/" + tk.path("id").asLong() + "/void"), "{\"reason\":\"平台退款失败\"}"), admin));
        money("50", balances().get(0).path("available"));

        // 线下到账的多收不能原路退回
        long offline = pi(null, null);
        confirmReceipt(offline, "1050", null, false, admin);
        convert(offline);
        req.put("ownerId", offline);
        req.put("attachmentIds", List.of(upload("REFUND")));
        assertEquals("这张单没有平台收款，不能原路退回", fail(call(json(post(CREDIT + "/overpaid"), write(req)), admin))
                .path("message").asText());
        JsonNode offRow = ok(call(get(CREDIT + "/overpaid"), admin)).get(0);
        assertEquals(0, offRow.path("platformReceipts").size());
        // 余额管理的转给客户不能选原路退回
        req.put("mode", "CREDIT");
        ok(call(json(post(CREDIT + "/overpaid"), write(req)), admin));
        Map<String, Object> w = withdraw("50", "355");
        w.put("method", 1);
        assertEquals("转出方式不正确", fail(call(json(post(CREDIT + "/withdraw"), write(w)), admin)).path("message").asText());
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
        line.put("productId", manualProduct("6ES7214-1AG40-0XB0"));
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

    // ---------------------------------------------------------------- 覆盖补充

    private void adjust(long c, String currency, String amount, String cny) throws Exception {
        Map<String, Object> b = new HashMap<>();
        b.put("customerId", c);
        b.put("currencyCode", currency);
        b.put("amount", amount);
        b.put("amountCny", cny);
        b.put("reason", "旧 ERP 余额迁移");
        ok(call(json(post(CREDIT + "/adjust"), write(b)), admin));
    }

    private long manualOrder(String price) throws Exception {
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("productId", manualProduct("6ES7214-1AG40-0XB0"));
        line.put("model", "6ES7214-1AG40-0XB0");
        line.put("quantity", 1);
        line.put("unitPrice", price);
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("customerId", customer);
        b.put("currencyCode", "USD");
        b.put("items", List.of(line));
        return ok(call(json(post(SO), write(b)), admin)).path("id").asLong();
    }

    private JsonNode orderReceipt(long so, String amount) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", amount);
        body.put("receiptDate", LocalDate.now().toString());
        body.put("bankAccountId", jdbc.queryForObject("select min(id) from tenant_bank_account where tenant_id = 0", Integer.class));
        body.put("feeDiff", false);
        return ok(call(json(post(SO + "/" + so + "/receipts"), write(body)), admin));
    }

    @Test
    void listFilters_summary_entriesAndReconciliationByUnit_export() throws Exception {
        long other = customer("Pacific Controls Pty", "Australia");
        long zero = customer("Wingrid Rocha", "Brazil");
        adjust(customer, "USD", "100", "710");
        adjust(other, "EUR", "50", "390");
        adjust(zero, "USD", "10", "71");
        adjust(zero, "USD", "-10", "1");
        TenantContext.setTenantId(0);
        ledger.credit(new CustomerCreditLedger.Account(customer, "USD", 777), FinanceConstants.CREDIT_ADJUST, new BigDecimal("30"),
                new BigDecimal("213"), new CustomerCreditLedger.Source("MANUAL", null, ""), "事业部余额");
        // 筛选：关键词、币种、事业部（0 表示没有事业部）、显示余额为 0 的、分页
        assertEquals(2, ok(call(get(CREDIT).param("keyword", "acrobot"), admin)).path("total").asInt());
        assertEquals("EUR", ok(call(get(CREDIT).param("currencyCode", "EUR"), admin)).path("records").get(0).path("currencyCode").asText());
        assertEquals(2, ok(call(get(CREDIT).param("businessUnitId", "0"), admin)).path("total").asInt());
        assertEquals(777, ok(call(get(CREDIT).param("businessUnitId", "777"), admin)).path("records").get(0).path("businessUnitId").asInt());
        assertEquals(3, ok(call(get(CREDIT), admin)).path("total").asInt());
        assertEquals(4, ok(call(get(CREDIT).param("showAll", "true"), admin)).path("total").asInt());
        assertEquals(1, ok(call(get(CREDIT).param("page", "2").param("pageSize", "2"), admin)).path("records").size());
        assertEquals(1, ok(call(get(CREDIT + "/entries").param("customerId", String.valueOf(customer)).param("currencyCode", "USD")
                .param("businessUnitId", "777"), admin)).size());
        // 草稿 PI 占用 → 汇总里「已被 PI 占用」
        long q = quotation(customer, 2, "USD", null, l("6ES7214-1AG40-0XB0", 1, "5000", "500"));
        JsonNode draft = ok(createPi(quotationItemIds(q)));
        Map<String, Object> body = saveBody(draft);
        ((List<?>) body.get("fees")).clear();
        body.put("creditAmount", "40");
        ok(savePi(draft.path("id").asLong(), body));
        JsonNode summary = ok(call(get(CREDIT + "/summary"), admin));
        money("40", summary.path("occupied").get(0).path("amount"));
        assertEquals(1, summary.path("occupied").get(0).path("count").asInt());
        // 余额为 0、本月没有变动的账户不出现在核对表：把 Wingrid 的流水挪到上个月
        jdbc.update("update fin_customer_credit_entry set create_time = date_sub(create_time, interval 40 day) where customer_id = ?", zero);
        String month = LocalDate.now().toString().substring(0, 7);
        JsonNode all = ok(call(get(CREDIT + "/reconciliation").param("month", month).param("currencyCode", "USD"), admin));
        assertEquals(2, all.path("rows").size());
        assertTrue(all.path("balanced").asBoolean());
        money("40", all.path("total").path("deduct").path("amount"));
        assertEquals(1, ok(call(get(CREDIT + "/reconciliation").param("month", month).param("currencyCode", "USD")
                .param("businessUnitId", "777"), admin)).path("rows").size());
        assertEquals(1, ok(call(get(CREDIT + "/reconciliation").param("month", month).param("currencyCode", "USD")
                .param("businessUnitId", "0"), admin)).path("rows").size());
        // 不传月份与币种：默认上月、USD
        JsonNode last = ok(call(get(CREDIT + "/reconciliation"), admin));
        assertEquals(LocalDate.now().minusMonths(1).toString().substring(0, 7), last.path("month").asText());
        assertEquals("USD", last.path("currencyCode").asText());
        // 导出与页面一致
        byte[] xlsx = perform(get(CREDIT + "/reconciliation/export").param("month", month).param("currencyCode", "USD"), admin)
                .getContentAsByteArray();
        try (XSSFWorkbook wb = new XSSFWorkbook(new java.io.ByteArrayInputStream(xlsx))) {
            var sheet = wb.getSheetAt(0);
            assertEquals("客户", sheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals("合计", sheet.getRow(sheet.getLastRowNum()).getCell(0).getStringCellValue());
        }
    }

    @Test
    void adjustAndWithdrawValidation_bankWithdraw_reverseColumnsInReconciliation() throws Exception {
        Map<String, Object> b = new HashMap<>();
        b.put("customerId", customer);
        b.put("currencyCode", "USD");
        b.put("amount", "0");
        b.put("reason", "x");
        assertEquals("调整金额不能为 0", fail(call(json(post(CREDIT + "/adjust"), write(b)), admin)).path("message").asText());
        b.put("amount", "10");
        assertEquals("请填写人民币价值", fail(call(json(post(CREDIT + "/adjust"), write(b)), admin)).path("message").asText());
        adjust(customer, "USD", "100", "710");
        Map<String, Object> w = withdraw("10", "70");
        w.put("withdrawDate", LocalDate.now().plusDays(1).toString());
        assertEquals("转出日期不能晚于今天", fail(call(json(post(CREDIT + "/withdraw"), write(w)), admin)).path("message").asText());
        w = withdraw("10", "70");
        w.put("method", 3);
        assertEquals("转出方式不正确", fail(call(json(post(CREDIT + "/withdraw"), write(w)), admin)).path("message").asText());
        w.put("method", 4);
        w.put("methodName", " ");
        assertEquals("请填写转出方式，如支付宝", fail(call(json(post(CREDIT + "/withdraw"), write(w)), admin)).path("message").asText());
        w = withdraw("10", "70");
        w.put("attachmentIds", List.of());
        assertEquals("请上传转账凭证", fail(call(json(post(CREDIT + "/withdraw"), write(w)), admin)).path("message").asText());
        // 银行汇款：转出方式显示「银行汇款」；作废后余额恢复，核对表里提现一进一出
        w = withdraw("10", "71");
        w.put("method", 2);
        w.remove("methodName");
        JsonNode tk = ok(call(json(post(CREDIT + "/withdraw"), write(w)), admin));
        assertEquals("银行汇款", tk.path("methodName").asText());
        ok(call(json(post(REFUND + "/" + tk.path("id").asLong() + "/void"), "{\"reason\":\"退回\"}"), admin));
        JsonNode r = ok(call(get(CREDIT + "/reconciliation").param("month", LocalDate.now().toString().substring(0, 7))
                .param("currencyCode", "USD"), admin)).path("total");
        money("0", r.path("withdraw").path("amount"));
        money("100", r.path("closing").path("amount"));
        assertTrue(ok(call(get(INCOME).param("category", "FX_GAIN"), admin)).path("records").isEmpty());
    }

    @Test
    void manualOrderOverpaid_processInParts_voidReceiptReversesProcessed() throws Exception {
        long so = manualOrder("250");
        orderReceipt(so, "300");
        JsonNode row = ok(call(get(CREDIT + "/overpaid"), admin)).get(0);
        assertEquals("SO", row.path("ownerType").asText());
        money("50", row.path("pending"));
        Map<String, Object> req = new HashMap<>();
        req.put("ownerType", "SO");
        req.put("ownerId", so);
        req.put("amount", "20");
        req.put("mode", "X");
        assertEquals("处理方式不正确", fail(call(json(post(CREDIT + "/overpaid"), write(req)), admin)).path("message").asText());
        req.put("mode", "REFUND");
        assertEquals("请填写转出日期、转出方式与实际转出人民币",
                fail(call(json(post(CREDIT + "/overpaid"), write(req)), admin)).path("message").asText());
        req.put("mode", "CREDIT");
        money("30", ok(call(json(post(CREDIT + "/overpaid"), write(req)), admin)).get(0).path("pending"));
        req.put("ownerId", -1);
        assertEquals("销售订单不存在", fail(call(json(post(CREDIT + "/overpaid"), write(req)), admin)).path("message").asText());
        req.put("ownerType", "PI");
        assertEquals("PI 不存在", fail(call(json(post(CREDIT + "/overpaid"), write(req)), admin)).path("message").asText());
        money("30", ok(call(get(SO + "/" + so), admin)).path("credit").path("overpaidPending"));
        money("20", balances().get(0).path("available"));
        // 作废这笔到账：多收没了，已处理的 20 还没被用，自动撤回
        long receipt = jdbc.queryForObject("select id from payment_receipt where so_id = ? and status = 1", Long.class, so);
        ok(call(json(post(SO + "/" + so + "/receipts/" + receipt + "/void"), "{\"reason\":\"录错\"}"), admin));
        money("0", balances().get(0).path("available"));
        assertTrue(ok(call(get(CREDIT + "/overpaid"), admin)).isEmpty());
    }

    @Test
    void voidReceiptBeforeWithdraw_reversesCollection() throws Exception {
        long pi = pi("300", null);
        platform(pi, "1300", "39");
        money("291", balances().get(0).path("available"));
        long receipt = jdbc.queryForObject("select id from payment_receipt where pi_id = ? and status = 1", Long.class, pi);
        ok(call(json(post(PI + "/" + pi + "/receipts/" + receipt + "/void"), "{\"reason\":\"录错\"}"), admin));
        money("0", balances().get(0).path("available"));
        assertFalse(piDetail(pi).path("credit").path("collectionTransferred").asBoolean());
    }

    @Test
    void newVersionRaisesCredit_receiptFollowsSentVersion_abandonRestores() throws Exception {
        adjust(customer, "USD", "200", "1400");
        long pi = pi(null, "100");
        money("100", balances().get(0).path("available"));
        ok(call(json(post(PI + "/" + pi + "/revise"), "{}"), admin));
        Map<String, Object> body = saveBody(piDetail(pi));
        ((List<?>) body.get("fees")).clear();
        body.put("creditAmount", "150");
        ok(savePi(pi, body));
        // 编辑中的版本占用 150；已到账仍按已发送版本的 100，价值按占用比例 1,050 × 100 ÷ 150
        money("50", balances().get(0).path("available"));
        Map<String, Object> rcpt = jdbc.queryForMap(
                "select amount, amount_cny from payment_receipt where pi_id = ? and payment_method = 'CREDIT' and status = 1", pi);
        assertEquals(0, new BigDecimal("100").compareTo((BigDecimal) rcpt.get("amount")));
        assertEquals(0, new BigDecimal("700.00").compareTo((BigDecimal) rcpt.get("amount_cny")));
        ok(call(json(post(PI + "/" + pi + "/abandon"), "{}"), admin));
        money("100", balances().get(0).path("available"));
    }

    @Test
    void creditOnlyOrder_refundToCreditUsesLockedRate_orderCreditNeedsAmount() throws Exception {
        adjust(customer, "USD", "100", "710");
        long so = manualOrder("100");
        assertEquals("抵扣金额须大于 0", fail(call(json(post(SO + "/" + so + "/credit"), "{}"), admin)).path("message").asText());
        ok(call(json(post(SO + "/" + so + "/credit"), "{\"amount\":100}"), admin));
        // 全部用余额付款：转余额的折算汇率取余额抵扣的锁定价值 7.10
        JsonNode tk = ok(call(json(post(REFUND), write(refund(so, "50", 3))), admin));
        money("355.00", tk.path("amountCny"));
        money("50", balances().get(0).path("available"));
    }

    @Test
    void pagingDefaults_unpaidOrderRate_deleteDraftReleases_salesViewScoped() throws Exception {
        adjust(customer, "USD", "100", "710");
        assertEquals(1, ok(call(get(CREDIT).param("page", "0").param("pageSize", "0"), admin)).path("records").size());
        // 没有任何到账的订单：转余额折算汇率为 1（可退金额为 0，不会真的转）
        long so = manualOrder("80");
        money("1", ok(call(get(REFUND + "/refundable/" + so), admin)).path("creditRate"));
        // 草稿 PI 占用后删除：释放
        long q = quotation(customer, 2, "USD", null, l("6ES7214-1AG40-0XB0", 1, "5000", "500"));
        JsonNode draft = ok(createPi(quotationItemIds(q)));
        long pi = draft.path("id").asLong();
        Map<String, Object> body = saveBody(draft);
        ((List<?>) body.get("fees")).clear();
        body.put("creditAmount", "60");
        ok(savePi(pi, body));
        money("40", balances().get(0).path("available"));
        ok(call(delete(PI + "/" + pi), admin));
        money("100", balances().get(0).path("available"));
        // 没有余额管理菜单的业务员：只能看数据范围内的客户
        jdbc.update("update customer set owner_id = 99000002 where id = ?", customer);
        loginWithResources("it_sales_rep", MENU_PI);
        String rep = token("it_sales_rep");
        assertEquals("客户不存在", fail(call(get(CREDIT + "/customer/-1"), rep)).path("message").asText());
        money("100", ok(call(get(CREDIT + "/customer/" + customer), rep)).get(0).path("available"));
    }

    @Test
    void overpaidOnUnconvertedPi_remarkKept_refundNeedsProof_convertedPiNotOccupied() throws Exception {
        adjust(customer, "USD", "30", "213");
        long pi = pi(null, "30");
        confirmReceipt(pi, "1000", null, false, admin);
        JsonNode row = ok(call(get(CREDIT + "/overpaid"), admin)).get(0);
        assertTrue(row.path("soId").isNull() || row.path("soId").isMissingNode());
        money("30", row.path("pending"));
        Map<String, Object> req = new HashMap<>();
        req.put("ownerType", "PI");
        req.put("ownerId", pi);
        req.put("amount", "10");
        req.put("mode", "REFUND");
        req.put("withdrawDate", LocalDate.now().toString());
        req.put("method", 4);
        req.put("methodName", "支付宝");
        req.put("actualCny", "70");
        req.put("attachmentIds", List.of());
        assertEquals("请上传转账凭证", fail(call(json(post(CREDIT + "/overpaid"), write(req)), admin)).path("message").asText());
        req.put("mode", "CREDIT");
        req.put("remark", "客户多付了运费");
        ok(call(json(post(CREDIT + "/overpaid"), write(req)), admin));
        JsonNode flows = ok(call(get(CREDIT + "/entries").param("customerId", String.valueOf(customer)).param("currencyCode", "USD"), admin));
        assertEquals("客户多付了运费", flows.get(0).path("remark").asText());
        // PI 还没转订单：抵扣算「占用中」；转订单后不再算
        money("30", ok(call(get(CREDIT), admin)).path("records").get(0).path("occupied"));
        convert(pi);
        money("0", ok(call(get(CREDIT), admin)).path("records").get(0).path("occupied"));
    }

    @Test
    void reconciliationPutsReversalsInTheirOwnColumns() throws Exception {
        // 售后退款转余额后作废、代收转入后作废到账：冲销记在原来的列，净额为 0
        long pi1 = pi(null, null);
        confirmReceipt(pi1, "1000", null, false, admin);
        long so = convert(pi1);
        JsonNode tk = ok(call(json(post(REFUND), write(refund(so, "100", 3))), admin));
        ok(call(json(post(REFUND + "/" + tk.path("id").asLong() + "/void"), "{\"reason\":\"录错\"}"), admin));
        long pi2 = pi("300", null);
        platform(pi2, "1300", "0");
        long receipt = jdbc.queryForObject("select id from payment_receipt where pi_id = ? and status = 1", Long.class, pi2);
        ok(call(json(post(PI + "/" + pi2 + "/receipts/" + receipt + "/void"), "{\"reason\":\"录错\"}"), admin));
        JsonNode t = ok(call(get(CREDIT + "/reconciliation").param("month", LocalDate.now().toString().substring(0, 7))
                .param("currencyCode", "USD"), admin));
        assertEquals(1, t.path("rows").size());
        money("0", t.path("total").path("refundIn").path("amount"));
        money("0", t.path("total").path("collectionIn").path("amount"));
        money("0", t.path("total").path("closing").path("amount"));
        assertTrue(t.path("balanced").asBoolean());
    }
}
