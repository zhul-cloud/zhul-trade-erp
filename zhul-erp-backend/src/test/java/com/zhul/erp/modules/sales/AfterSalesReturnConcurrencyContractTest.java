package com.zhul.erp.modules.sales;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.aftersales.dto.ConfirmReturnRequest;
import com.zhul.erp.modules.aftersales.dto.ReceiveReturnRequest;
import com.zhul.erp.modules.aftersales.service.ReturnReceiptService;
import com.zhul.erp.modules.aftersales.service.SalesReturnService;
import com.zhul.erp.modules.aftersales.support.AfterSalesLogs;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * audit BUS-035（BUS-035#2）：MySQL 默认可重复读下，事务里第一次普通读就建立了读视图，加锁后的重读仍是旧快照。
 * 第二个请求在第一个请求提交前完成首次读取、随后等锁；第一个提交后，第二个加锁重读必须看到最新状态并被拒绝，
 * 同一张销售退货单只生成一张退货入库单、一条退款申请，同一张退货入库单只验收一次。
 * 用 SpyBean 把第一个请求卡在写售后跟进记录处（已持有全部行锁、还没提交）。
 */
class AfterSalesReturnConcurrencyContractTest extends SalesContractSupport {

    private static final String AS = "/api/v1/sales/after-sales";
    private static final String TH = "/api/v1/sales/returns";

    @Autowired private SalesReturnService returns;
    @Autowired private ReturnReceiptService receipts;
    @SpyBean private AfterSalesLogs logs;

    /** 手动订单 A-9 × 4，已到账 500，全部出运 */
    private long manualShipped(long customer) throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("customerId", customer);
        m.put("items", List.of(Map.of("productId", manualProduct("A-9"), "quantity", 4, "unitPrice", "100", "costPrice", "300")));
        long so = ok(call(json(post(SO), write(m)), admin)).path("id").asLong();
        Map<String, Object> r = new HashMap<>();
        r.put("amount", "500");
        r.put("receiptDate", LocalDate.now().toString());
        r.put("bankAccountId", jdbc.queryForObject("select min(id) from tenant_bank_account where tenant_id = 0", Integer.class));
        ok(call(json(post(SO + "/" + so + "/receipts"), write(r)), admin));
        jdbc.update("insert into logistics_shipment (tenant_id, sh_no, status) values (0, ?, 2)", "ITSH" + System.nanoTime());
        long ls = jdbc.queryForObject("select max(id) from logistics_shipment where tenant_id = 0", Long.class);
        jdbc.update("insert into outbound_order (tenant_id, ob_no, so_id, status, logistics_id) values (0, ?, ?, 3, ?)", "ITOBC" + so, so, ls);
        long ob = jdbc.queryForObject("select max(id) from outbound_order where tenant_id = 0", Long.class);
        jdbc.update("insert into outbound_order_item (tenant_id, outbound_id, so_item_id, quantity) values (0, ?, ?, 4)", ob, soItem(so));
        return so;
    }

    private long soItem(long so) {
        return jdbc.queryForObject("select id from sales_order_item where so_id = ? order by line_no limit 1", Long.class, so);
    }

    /** 退货退款、待确认的销售退货单 */
    private long pendingReturn() throws Exception {
        bankAccount("USD");
        long c = customer("Race Controls", "Poland");
        long so = manualShipped(c);
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("customerId", c);
        b.put("soId", so);
        b.put("items", List.of(Map.of("soItemId", soItem(so), "quantity", 1)));
        b.put("symptom", "无法上电");
        long asId = ok(call(json(post(AS), write(b)), admin)).path("id").asLong();
        return ok(call(json(post(TH), write(Map.of("afterSalesId", asId, "handling", 2,
                "items", List.of(Map.of("soItemId", soItem(so), "quantity", 1))))), admin)).path("id").asLong();
    }

    /** 第一个请求在写跟进记录时停住（此时已持有锁、还没提交），返回「已停住」的信号与放行开关 */
    private CountDownLatch[] pauseFirst(AtomicReference<Thread> first) {
        CountDownLatch paused = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        doAnswer(inv -> {
            if (Thread.currentThread() == first.get()) {
                paused.countDown();
                resume.await(10, TimeUnit.SECONDS);
            }
            return inv.callRealMethod();
        }).when(logs).add(anyLong(), anyString(), anyString(), any());
        return new CountDownLatch[] {paused, resume};
    }

    /** 两个请求交错：第一个停住后发第二个，等它完成首次读取并卡在锁上，再放行第一个；返回第二个的结果 */
    private CompletableFuture<Void> interleave(Runnable action) throws Exception {
        AtomicReference<Thread> first = new AtomicReference<>();
        CountDownLatch[] gate = pauseFirst(first);
        CompletableFuture<Void> a = CompletableFuture.runAsync(() -> as(() -> {
            first.set(Thread.currentThread());
            action.run();
        }));
        assertTrue(gate[0].await(15, TimeUnit.SECONDS), "第一个请求应已停在提交前");
        CompletableFuture<Void> b = CompletableFuture.runAsync(() -> as(action));
        try {
            b.get(1500, TimeUnit.MILLISECONDS);
        } catch (TimeoutException expected) {
            // 第二个请求已读过一次，正在等第一个请求的行锁
        } catch (ExecutionException ignored) {
            // 修复前后都不应在拿到锁前失败；失败时下面的断言会给出原因
        }
        gate[1].countDown();
        a.get(15, TimeUnit.SECONDS);
        return b;
    }

    private static String failure(CompletableFuture<Void> f) {
        ExecutionException e = assertThrows(ExecutionException.class, () -> f.get(15, TimeUnit.SECONDS), "第二个请求应被拒绝");
        assertInstanceOf(BizException.class, e.getCause(), String.valueOf(e.getCause()));
        return e.getCause().getMessage();
    }

    @Test
    void confirmTwice_concurrently_createsOneReceiptAndOneRefundRequest() throws Exception {
        long thId = pendingReturn();
        ConfirmReturnRequest req = objectMapper.readValue("{\"refund\":{\"method\":3}}", ConfirmReturnRequest.class);

        CompletableFuture<Void> second = interleave(() -> returns.confirm(thId, req));

        assertEquals("销售退货单待入库，不能确认", failure(second));
        assertEquals(1, jdbc.queryForObject("select count(*) from return_receipt where sales_return_id = ?", Integer.class, thId),
                "只生成一张退货入库单");
        assertEquals(1, jdbc.queryForObject("select count(*) from fin_refund_request where sales_return_id = ?", Integer.class, thId),
                "只生成一条退款申请");
    }

    @Test
    void receiveTwice_concurrently_receivesOnce() throws Exception {
        long thId = pendingReturn();
        JsonNode th = ok(call(json(post(TH + "/" + thId + "/confirm"), write(Map.of("refund", Map.of("method", 3)))), admin));
        long rrId = th.path("receipt").path("id").asLong();
        long itemId = ok(call(get("/api/v1/warehouse/returns/" + rrId), admin)).path("items").get(0).path("id").asLong();
        ReceiveReturnRequest req = objectMapper.readValue(write(Map.of("receivedDate", LocalDate.now().toString(),
                "items", List.of(Map.of("itemId", itemId, "receivedQty", 1, "qualifiedQty", 1)))), ReceiveReturnRequest.class);

        CompletableFuture<Void> second = interleave(() -> receipts.receive(rrId, req));

        assertEquals("退货入库单已验收", failure(second));
        assertEquals(1, jdbc.queryForObject("select count(*) from document_log where doc_type = 'RR' and doc_id = ? and action = '验收'", Integer.class, rrId),
                "只验收一次");
    }

    private static void as(Runnable action) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("it_sales_admin", null, List.of()));
        TenantContext.setTenantId(0);
        try {
            action.run();
        } finally {
            TenantContext.clear();
            SecurityContextHolder.clearContext();
        }
    }
}
