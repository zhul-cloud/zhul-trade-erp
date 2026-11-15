package com.zhul.erp.modules.finance;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhul.erp.modules.finance.service.CreditUsageService;
import com.zhul.erp.modules.sales.SalesContractSupport;
import com.zhul.erp.modules.system.service.LogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.SpyBean;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * audit BUS-035：余额与退款申请先普通读、后加锁时，MySQL 可重复读下锁后的读取仍用加锁前的快照。
 * 做法：先发的请求 A 写完、提交前卡住；后发的请求 B 此时开始（首次普通读发生在 A 提交前），随后等 A 的行锁；
 * 放行 A 提交后，B 拿到锁必须看到 A 写入的占用、多收转入或退款申请，并按不足拒绝。
 */
class CreditConcurrencyContractTest extends SalesContractSupport {

    private static final String CREDIT = "/api/v1/finance/credits";
    private static final String RQ = "/api/v1/finance/refund-requests";

    @SpyBean private LogService logService;
    @SpyBean private CreditUsageService creditUsage;

    private final AtomicReference<Thread> first = new AtomicReference<>();
    private final CountDownLatch firstWritten = new CountDownLatch(1);
    private final CountDownLatch resumeFirst = new CountDownLatch(1);

    private long customer;

    @BeforeEach
    void setUp() {
        bankAccount("USD");
        customer = customer("ACROBOT Automacao", "Brazil");
    }

    /** 请求 A 在写完后（提交前）卡住 */
    private Object pauseFirst(org.mockito.invocation.InvocationOnMock inv) throws Throwable {
        Object r = inv.callRealMethod();
        if (Thread.currentThread() == first.get()) {
            firstWritten.countDown();
            resumeFirst.await(10, TimeUnit.SECONDS);
        }
        return r;
    }

    /** 先发 A，等它写完卡住后发 B；B 应在等 A 的锁，放行 A 后返回两者的响应 */
    private List<JsonNode> race(Callable<JsonNode> a, Callable<JsonNode> b) throws Exception {
        CompletableFuture<JsonNode> fa = CompletableFuture.supplyAsync(() -> {
            first.set(Thread.currentThread());
            return unchecked(a);
        });
        assertTrue(firstWritten.await(10, TimeUnit.SECONDS), "请求 A 应已写完");
        CompletableFuture<JsonNode> fb = CompletableFuture.supplyAsync(() -> unchecked(b));
        try {
            fb.get(1500, TimeUnit.MILLISECONDS);
        } catch (TimeoutException expected) {
            // B 在等 A 的行锁
        }
        resumeFirst.countDown();
        return List.of(fa.get(15, TimeUnit.SECONDS), fb.get(15, TimeUnit.SECONDS));
    }

    private static JsonNode unchecked(Callable<JsonNode> c) {
        try {
            return c.call();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** 草稿 PI：型号 1000.00 */
    private JsonNode draftPi() throws Exception {
        long q = quotation(customer, 2, "USD", null, l("6ES7214-1AG40-0XB0", 1, "5000", "1000"));
        return ok(createPi(quotationItemIds(q)));
    }

    private Map<String, Object> withCredit(JsonNode pi, String credit) throws Exception {
        Map<String, Object> body = saveBody(pi);
        body.put("bankAccountId", jdbc.queryForObject("select min(id) from tenant_bank_account where tenant_id = 0", Integer.class));
        body.put("creditAmount", credit);
        return body;
    }

    private long sentPi() throws Exception {
        JsonNode pi = draftPi();
        long id = pi.path("id").asLong();
        Map<String, Object> body = saveBody(pi);
        body.put("bankAccountId", jdbc.queryForObject("select min(id) from tenant_bank_account where tenant_id = 0", Integer.class));
        ok(savePi(id, body));
        ok(send(id));
        return id;
    }

    @Test
    void twoPisDeductSameCredit_secondSeesFirstOccupation() throws Exception {
        Map<String, Object> adj = new HashMap<>();
        adj.put("customerId", customer);
        adj.put("currencyCode", "USD");
        adj.put("amount", "100");
        adj.put("amountCny", "715");
        adj.put("reason", "旧 ERP 余额迁移");
        ok(call(json(post(CREDIT + "/adjust"), write(adj)), admin));
        JsonNode p1 = draftPi();
        JsonNode p2 = draftPi();
        Map<String, Object> b1 = withCredit(p1, "100");
        Map<String, Object> b2 = withCredit(p2, "100");
        doAnswer(this::pauseFirst).when(creditUsage).syncPi(any());

        List<JsonNode> res = race(() -> savePi(p1.path("id").asLong(), b1), () -> savePi(p2.path("id").asLong(), b2));

        ok(res.get(0));
        String msg = fail(res.get(1)).path("message").asText();
        assertTrue(msg.startsWith("可用余额只有 USD 0.00"), msg);
        assertEquals(0, BigDecimal.ZERO.compareTo(jdbc.queryForObject(
                "select sum(amount) from fin_customer_credit_entry where tenant_id = 0 and customer_id = ?", BigDecimal.class, customer)),
                "余额不能被两张 PI 重复抵扣成负数");
    }

    @Test
    void overpaidProcessedTwiceAtOnce_secondSeesFirstTransfer() throws Exception {
        long pi = sentPi();
        confirmReceipt(pi, "1050", null, false, admin);
        Map<String, Object> req = new HashMap<>();
        req.put("ownerType", "PI");
        req.put("ownerId", pi);
        req.put("amount", "50");
        req.put("mode", "CREDIT");
        String body = write(req);
        doAnswer(this::pauseFirst).when(logService).recordOperateLog(anyString(), anyString(), any(), any());

        List<JsonNode> res = race(() -> call(json(post(CREDIT + "/overpaid"), body), admin),
                () -> call(json(post(CREDIT + "/overpaid"), body), admin));

        ok(res.get(0));
        assertEquals("待处理多收只有 USD 0.00", fail(res.get(1)).path("message").asText());
        assertEquals(1, jdbc.queryForObject("select count(*) from fin_customer_credit_entry where tenant_id = 0 and customer_id = ?",
                Integer.class, customer));
    }

    @Test
    void refundAppliedTwiceAtOnce_secondSeesFirstRequest() throws Exception {
        long pi = sentPi();
        confirmReceipt(pi, "1000", null, false, admin);
        ok(call(json(post(PI + "/" + pi + "/convert"), "{}"), admin));
        long so = jdbc.queryForObject("select max(id) from sales_order where tenant_id = 0 and pi_id = ?", Long.class, pi);
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("soId", so);
        req.put("amount", "1000");
        req.put("reason", "QUALITY");
        req.put("method", 3);
        String body = write(req);
        doAnswer(this::pauseFirst).when(logService).recordOperateLog(anyString(), anyString(), any(), any());

        List<JsonNode> res = race(() -> call(json(post(RQ), body), admin), () -> call(json(post(RQ), body), admin));

        ok(res.get(0));
        assertEquals("最多还能申请 USD 0.00", fail(res.get(1)).path("message").asText());
        assertEquals(1, jdbc.queryForObject("select count(*) from fin_refund_request where tenant_id = 0 and so_id = ?",
                Integer.class, so));
    }
}
