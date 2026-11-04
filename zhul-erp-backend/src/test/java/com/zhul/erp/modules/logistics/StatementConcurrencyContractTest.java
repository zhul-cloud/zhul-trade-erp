package com.zhul.erp.modules.logistics;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhul.erp.modules.logistics.constants.LogisticsConstants;
import com.zhul.erp.modules.sales.SalesContractSupport;
import com.zhul.erp.modules.system.service.LogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.SpyBean;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** audit BUS-007：同一货代同一月份并发新建运费账单，同一出运单只能进一张账单、只生成一笔运费应付 */
class StatementConcurrencyContractTest extends SalesContractSupport {

    private static final String ST = "/api/v1/logistics/statements";

    @SpyBean private LogService logService;

    private long forwarder;
    private long shipment;
    private String period;

    @BeforeEach
    void data() {
        forwarder = supplier("并发对账货代", null);
        jdbc.update("update supplier set supplier_type = 3, volume_divisor = 5000 where id = ?", forwarder);
        jdbc.update("insert into logistics_shipment (tenant_id, sh_no, customer_id, forwarder_id, status, reconciled, shipped_date, freight) "
                + "values (0, 'ITSH-RACE', 0, ?, 2, 0, ?, 100.00)", forwarder, LocalDate.now());
        shipment = jdbc.queryForObject("select id from logistics_shipment where sh_no = 'ITSH-RACE'", Long.class);
        period = LocalDate.now().toString().substring(0, 7);
    }

    @Test
    void concurrentCreate_shipmentInOnlyOneStatement() throws Exception {
        CountDownLatch inserted = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        AtomicReference<Thread> first = new AtomicReference<>();
        // 第一个请求写完账单行、还没提交时停住（新建账单最后一步写操作日志）
        doAnswer(inv -> {
            if (Thread.currentThread() == first.get()) {
                inserted.countDown();
                resume.await(10, TimeUnit.SECONDS);
            }
            return inv.callRealMethod();
        }).when(logService).recordOperateLog(eq(LogisticsConstants.MENU_STATEMENT), eq("新建运费账单"), any(), any());
        String body = write(Map.of("forwarderId", forwarder, "period", period));

        CompletableFuture<JsonNode> a = CompletableFuture.supplyAsync(() -> {
            first.set(Thread.currentThread());
            return unchecked(body);
        });
        assertTrue(inserted.await(10, TimeUnit.SECONDS), "第一张账单已写入行");
        CompletableFuture<JsonNode> b = CompletableFuture.supplyAsync(() -> unchecked(body));
        try {
            b.get(1500, TimeUnit.MILLISECONDS);
        } catch (TimeoutException waiting) {
            // 修复后第二个请求在等出运单行锁
        }
        resume.countDown();
        long stA = ok(a.get(15, TimeUnit.SECONDS)).path("id").asLong();
        JsonNode rb = b.get(15, TimeUnit.SECONDS);
        assertEquals("这个月没有要对账的出运单", fail(rb).path("message").asText(), "第二张不能再纳入同一出运单");
        assertEquals(1, jdbc.queryForObject("select count(*) from forwarder_statement_line where logistics_id = ? and deleted_at is null",
                Integer.class, shipment));

        // 历史上已重复的：另一张草稿也含这张出运单，先确认的生效，后确认的被拒绝，只生成一笔应付
        long line = jdbc.queryForObject("select id from forwarder_statement_line where statement_id = ?", Long.class, stA);
        jdbc.update("insert into forwarder_statement (tenant_id, forwarder_id, period, status, our_total, statement_total) "
                + "values (0, ?, ?, 1, 100.00, 100.00)", forwarder, period);
        long dup = jdbc.queryForObject("select max(id) from forwarder_statement where forwarder_id = ?", Long.class, forwarder);
        jdbc.update("insert into forwarder_statement_line (tenant_id, statement_id, logistics_id, our_freight, statement_amount, note) "
                + "values (0, ?, ?, 100.00, 100.00, '')", dup, shipment);
        ok(call(json(post(ST + "/" + stA + "/confirm"), write(Map.of("lines", List.of(Map.of("id", line, "statementAmount", "100"))))), admin));
        assertEquals("出运单 ITSH-RACE 已在别的运费账单上确认过",
                fail(call(json(post(ST + "/" + dup + "/confirm"), "{}"), admin)).path("message").asText());
        assertEquals(1, jdbc.queryForObject("select count(*) from fin_payable where source_type = 2 and deleted_at is null "
                + "and source_id in (?, ?)", Integer.class, stA, dup), "只生成一笔运费应付");
    }

    private JsonNode unchecked(String body) {
        try {
            return call(json(post(ST), body), admin);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
