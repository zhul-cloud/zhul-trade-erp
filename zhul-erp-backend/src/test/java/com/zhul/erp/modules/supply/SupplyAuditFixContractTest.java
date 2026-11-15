package com.zhul.erp.modules.supply;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.common.constants.RedisKeyConstants;
import com.zhul.erp.common.utils.JwtUtils;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.quotation.event.QuoteSupplyConfirmedEvent;
import com.zhul.erp.modules.quotation.support.QuoteSupplyHints;
import com.zhul.erp.modules.supply.dto.SupplyRequests;
import com.zhul.erp.modules.supply.job.SupplyDailyJob;
import com.zhul.erp.modules.supply.service.DemandService;
import com.zhul.erp.modules.supply.service.SupplyBoardService;
import com.zhul.erp.modules.supply.support.DemandRecalc;
import com.zhul.erp.modules.supply.support.EvidenceIndex;
import com.zhul.erp.modules.supply.support.QuoteLinkage;
import com.zhul.erp.modules.supply.support.QuoteSupplyCheck;
import com.zhul.erp.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 供应链后端审查整改（audit BUS-054、BUS-073、BUS-074、BUS-075、BUS-076、BUS-077、SEC-023）的缺陷见证。
 * 服务层用例用独立租户 {@link #TENANT}；开发任务时间线（SEC-023）走接口，用平台租户 0、名称前缀 ITAU。
 */
@AutoConfigureMockMvc
class SupplyAuditFixContractTest extends IntegrationTestBase {

    private static final int TENANT = 99000087;
    private static final String API = "/api/v1/supply";
    private static final int MENU_FOLLOWUP = 110236;

    @Autowired private SupplyDailyJob job;
    @Autowired private QuoteLinkage linkage;
    @Autowired private DemandService demandService;
    @Autowired private DemandRecalc recalc;
    @Autowired private SupplyBoardService boardService;
    @Autowired private QuoteSupplyCheck quoteSupplyCheck;
    @Autowired private PlatformTransactionManager txManager;
    @SpyBean private EvidenceIndex evidenceIndex;
    @Autowired private MockMvc mvc;
    @Autowired private JwtUtils jwtUtils;
    @Autowired private StringRedisTemplate redis;
    @Autowired private ObjectMapper objectMapper;

    private long brandId;

    @BeforeEach
    void data() {
        cleanup();
        jdbc.update("insert into product_brand (tenant_id, brand_name, status) values (0, 'ITAUBRAND', 1)");
        brandId = jdbc.queryForObject("select id from product_brand where brand_name = 'ITAUBRAND'", Long.class);
    }

    @AfterEach
    void cleanup() {
        for (String t : List.of("sc_gap_project_item", "sc_gap_project", "sc_followup_log", "sc_coverage_channel",
                "sc_demand_signal", "sc_demand", "sc_dev_candidate", "document_log", "sourcing_quote", "sourcing_task",
                "inquiry_item", "sales_order_item", "sales_order", "supplier")) {
            jdbc.update("delete from " + t + " where tenant_id = ?", TENANT);
        }
        jdbc.update("delete from sc_dev_candidate where tenant_id = 0 and project_id in"
                + " (select id from sc_gap_project where name like 'ITAU%')");
        jdbc.update("delete from sc_followup_log where tenant_id = 0 and gap_project_id in"
                + " (select id from sc_gap_project where name like 'ITAU%')");
        jdbc.update("delete from document_log where tenant_id = 0 and doc_type = 'GP' and doc_id in"
                + " (select id from sc_gap_project where name like 'ITAU%')");
        jdbc.update("delete from sc_gap_project where tenant_id = 0 and name like 'ITAU%'");
        jdbc.update("delete from supplier where supplier_code like 'ITAU-%'");
        jdbc.update("delete from product_category where category_code in ('itau_l2', 'itau_l1')");
        jdbc.update("delete from product_brand where brand_name = 'ITAUBRAND'");
    }

    /** audit BUS-054：售后补发订单不算成交信号；已汇入的补发信号再次看到时标为来源已取消 */
    @Test
    void bus054_replacementOrderIsNotADealSignal() {
        long repl = orderItem("ITAU-SO-R", 3, "ITAU-R1");
        long normal = orderItem("ITAU-SO-N", 1, "ITAU-N1");
        // 修复前已汇入的补发信号（模拟线上旧数据）
        jdbc.update("insert into sc_demand_signal (tenant_id, source_type, source_id, brand_id, model_raw, model_key,"
                + " occurred_at, demand_id) values (?, 2, ?, ?, 'ITAU-R1', 'itaur1', now(), 0)", TENANT, repl, brandId);

        TenantContext.runAs(TENANT, () -> job.runTenant(TENANT, LocalDate.now()));

        assertEquals(1, jdbc.queryForObject("select source_cancelled from sc_demand_signal where tenant_id = ?"
                + " and source_type = 2 and source_id = ?", Integer.class, TENANT, repl), "补发订单的型号行不计成交");
        assertEquals(0, jdbc.queryForObject("select source_cancelled from sc_demand_signal where tenant_id = ?"
                + " and source_type = 2 and source_id = ?", Integer.class, TENANT, normal), "普通订单照常算成交");
        assertEquals(2, jdbc.queryForObject("select count(*) from sc_demand_signal where tenant_id = ? and source_type = 2",
                Integer.class, TENANT), "补发订单不新增成交信号：只有旧数据那一条和普通订单一条");
    }

    /** audit BUS-073：待审核回价被退回（从未生效）不登记「回价已撤回」；生效过再撤回的照常登记 */
    @Test
    void bus073_rejectedReviewIsNotWithdrawal() {
        long quote = quoteWithDemand("ITAUQ1", 1);
        TenantContext.runAs(TENANT, () -> linkage.backfill(LocalDateTime.now().minusDays(2)));
        assertEquals(0, logs(3), "从未生效的回价被退回，不写撤回记录");

        jdbc.update("update sourcing_quote set status = 2, update_time = now() where id = ?", quote);
        TenantContext.runAs(TENANT, () -> linkage.backfill(LocalDateTime.now().minusDays(2)));
        assertEquals(1, logs(1), "生效后登记回价");
        jdbc.update("update sourcing_quote set status = 1, update_time = now() where id = ?", quote);
        TenantContext.runAs(TENANT, () -> linkage.backfill(LocalDateTime.now().minusDays(2)));
        assertEquals(1, logs(3), "生效过的回价撤回，补登撤回记录");
    }

    /** audit BUS-073：兼职采购自己撤回待审核的回价（撤回事件路径），从未生效也不登记「回价已撤回」 */
    @Test
    void bus073_withdrawPendingReviewByEvent() {
        long quote = quoteWithDemand("ITAUQ2", 3);
        jdbc.update("update sourcing_quote set status = 1, update_time = now() where id = ?", quote);
        TenantContext.runAs(TENANT, () -> linkage.handle(List.of(quote), true));
        assertEquals(0, logs(3), "待审核回价撤回成草稿，不写撤回记录");

        jdbc.update("update sourcing_quote set status = 2, update_time = now() where id = ?", quote);
        TenantContext.runAs(TENANT, () -> linkage.handle(List.of(quote), false));
        jdbc.update("update sourcing_quote set status = 1, update_time = now() where id = ?", quote);
        TenantContext.runAs(TENANT, () -> linkage.handle(List.of(quote), true));
        assertEquals(1, logs(3), "生效过的回价经撤回事件撤回，照常登记");
    }

    /** audit BUS-074：合并时被合并方的渠道并到保留方（去重、主渠道降为备选），专项型号换成保留方 */
    @Test
    void bus074_mergeMovesChannelsAndProjectItems() {
        long a = demand("ITAUA", 2, 3, null);
        long b = demand("ITAUB", 2, 3, null);
        long s1 = supplier("ITAU-S1");
        long s2 = supplier("ITAU-S2");
        long s3 = supplier("ITAU-S3");
        channel(a, s1, 1, 0);
        channel(b, s2, 1, 0);
        channel(b, s1, 2, 1);
        channel(b, s3, 2, 2);
        long p1 = project("ITAU 专项1", b);
        long p2 = project("ITAU 专项2", a, b);

        SupplyRequests.Merge req = new SupplyRequests.Merge();
        req.setKeepId(a);
        req.setRemoveId(b);
        TenantContext.runAs(TENANT, () -> demandService.merge(req));

        List<Map<String, Object>> ch = jdbc.queryForList("select supplier_id, role from sc_coverage_channel where demand_id = ?"
                + " and deleted_at is null order by role, sort_order", a);
        assertEquals(List.of(s1, s2, s3), ch.stream().map(m -> ((Number) m.get("supplier_id")).longValue()).toList(),
                "渠道并到保留方，同一供应商只留一条：" + ch);
        assertEquals(List.of(1, 2, 2), ch.stream().map(m -> ((Number) m.get("role")).intValue()).toList(),
                "保留方已有主渠道，被合并方的主渠道降为备选");
        assertEquals(0, jdbc.queryForObject("select count(*) from sc_coverage_channel where demand_id = ? and deleted_at is null",
                Integer.class, b));
        assertEquals(List.of(a), activeItems(p1), "专项里的被合并型号换成保留方");
        assertEquals(List.of(a), activeItems(p2), "专项已含保留方，不重复");
    }

    /** audit BUS-075：每日重算读取期间用户改了优先级并提交，重算不能用旧快照把人工优先级覆盖回建议值 */
    @Test
    void bus075_recalcKeepsConcurrentManualPriority() throws Exception {
        long d = demand("ITAUP1", 1, 1, null);
        jdbc.update("update sc_demand set cnt_inquiry = 5 where id = ?", d);
        TransactionTemplate tx = new TransactionTemplate(txManager);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Throwable> err = new AtomicReference<>();
        // 用户改优先级：已写未提交，持有行锁
        Thread user = new Thread(() -> {
            try {
                tx.executeWithoutResult(s -> {
                    jdbc.update("update sc_demand set priority = 3, priority_manual = 1, priority_reason = 'IT' where id = ?", d);
                    locked.countDown();
                    try {
                        assertTrue(release.await(20, TimeUnit.SECONDS));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            } catch (Throwable e) {
                err.set(e);
            }
        });
        user.start();
        assertTrue(locked.await(10, TimeUnit.SECONDS));
        Thread daily = new Thread(() -> {
            try {
                TenantContext.runAs(TENANT, () -> new TransactionTemplate(txManager)
                        .execute(s -> recalc.run(LocalDate.now(), Map.of())));
            } catch (Throwable e) {
                err.set(e);
            }
        });
        daily.start();
        // 等重算被用户的行锁挡住（修复前挡在回写，修复后挡在读取），再让用户提交
        long deadline = System.currentTimeMillis() + 15000;
        while (lockWaits() == 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertTrue(lockWaits() > 0, "重算应等待用户的行锁");
        release.countDown();
        user.join(20000);
        daily.join(20000);
        assertNull(err.get(), String.valueOf(err.get()));
        Map<String, Object> row = jdbc.queryForMap("select priority, priority_manual + 0 as priority_manual, cnt_inquiry"
                + " from sc_demand where id = ?", d);
        assertEquals(1, ((Number) row.get("priority_manual")).intValue());
        assertEquals(3, ((Number) row.get("priority")).intValue(), "人工优先级不被重算覆盖：" + row);
        assertEquals(0, ((Number) row.get("cnt_inquiry")).intValue(), "重算仍更新统计字段");
    }

    /** audit BUS-076：记一次处理改多个型号的覆盖状态，证据只批量读取一次，不随型号数增长 */
    @Test
    void bus076_recordLoadsEvidenceOnce() {
        long a = demand("ITAUE1", 2, 3, null);
        long b = demand("ITAUE2", 2, 3, null);
        long c = demand("ITAUE3", 2, 3, null);
        SupplyRequests.Record req = new SupplyRequests.Record();
        req.setDemandIds(List.of(a, b, c));
        req.setContent("IT 批量确认");
        req.setCoverage(3);
        req.setNextAction("IT 下周回访");
        req.setNextActionDate(LocalDate.now().plusDays(7));
        clearInvocations(evidenceIndex);
        TenantContext.runAs(TENANT, () -> new TransactionTemplate(txManager).execute(s -> boardService.record(req)));
        // 一次给覆盖状态校验用，一次给记录后的重算用
        verify(evidenceIndex, times(2)).load(any());
        assertEquals(3, jdbc.queryForObject("select count(*) from sc_demand where tenant_id = ? and coverage_status = 3",
                Integer.class, TENANT));
    }

    /** audit BUS-077：报价时用被合并方的型号，供应提示与发送确认沿合并链落到保留方 */
    @Test
    void bus077_quoteHintsFollowMerge() {
        long keep = demand("itauy", 2, 3, 3);
        long gone = demand("itaux", 1, 1, null);
        jdbc.update("update sc_demand set deleted_at = now(), merged_into_id = ? where id = ?", keep, gone);

        AtomicReference<Map<Long, List<String>>> out = new AtomicReference<>();
        TenantContext.runAs(TENANT, () -> out.set(quoteSupplyCheck.hints(
                List.of(new QuoteSupplyHints.Line(990087L, "ITAUBRAND", "ITAU-X", null)))));
        Map<Long, List<String>> hints = out.get();
        assertEquals(List.of("货源需确认"), hints.get(990087L), "按保留方的覆盖状态提示：" + hints);

        QuoteSupplyConfirmedEvent e = new QuoteSupplyConfirmedEvent(TENANT, "ITAU-QT1", 0L,
                List.of(new QuoteSupplyConfirmedEvent.Line(990087L, "ITAUBRAND", "ITAU-X", List.of("货源需确认"))));
        AtomicReference<Integer> n = new AtomicReference<>();
        TenantContext.runAs(TENANT, () -> n.set(new TransactionTemplate(txManager).execute(s -> quoteSupplyCheck.record(e))));
        assertEquals(1, n.get(), "发送确认登记到保留方");
        assertEquals("[" + keep + "]", jdbc.queryForObject("select demand_ids from sc_followup_log where tenant_id = ?"
                + " and source_type = 5", String.class, TENANT));
    }

    /** audit SEC-023：没有「查看货源信息」时，开发任务时间线里加候选、关联供应商库的记录不显示名称 */
    @Test
    void sec023_candidateNamesHiddenInDevTimeline() throws Exception {
        jdbc.update("insert into product_category (tenant_id, category_code, category_name, category_name_zh, parent_id, status,"
                + " create_by) values (0, 'itau_l1', 'itau_l1', 'IT审一级', null, 1, 'itau')");
        long parent = jdbc.queryForObject("select id from product_category where category_code = 'itau_l1'", Long.class);
        jdbc.update("insert into product_category (tenant_id, category_code, category_name, category_name_zh, parent_id, status,"
                + " create_by) values (0, 'itau_l2', 'itau_l2', 'IT审伺服', ?, 1, 'itau')", parent);
        long category = jdbc.queryForObject("select id from product_category where category_code = 'itau_l2'", Long.class);
        jdbc.update("insert into supplier (tenant_id, supplier_code, name, status) values (0, 'ITAU-LIB1', 'IT甲供应商', 1),"
                + " (0, 'ITAU-LIB2', 'IT丙供应商', 1)");
        long lib1 = jdbc.queryForObject("select id from supplier where supplier_code = 'ITAU-LIB1'", Long.class);
        long lib2 = jdbc.queryForObject("select id from supplier where supplier_code = 'ITAU-LIB2'", Long.class);

        loginAsAdmin("it_au_admin");
        String admin = token("it_au_admin");
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("brandId", brandId);
        p.put("categoryId", category);
        p.put("name", "ITAU 伺服开发");
        p.put("goal", "开发 1 家");
        p.put("ownerId", 99000002L);
        p.put("dueDate", LocalDate.now().plusDays(30).toString());
        p.put("supplierTarget", 1);
        long project = ok(call(json(post(API + "/projects"), write(p)), admin)).asLong();
        ok(call(json(post(API + "/projects/" + project + "/candidates"), write(Map.of("supplierId", lib1))), admin));
        long manual = ok(call(json(post(API + "/projects/" + project + "/candidates"),
                write(Map.of("name", "IT乙代理", "source", 2))), admin)).asLong();
        ok(call(json(post(API + "/candidates/" + manual + "/link"), write(Map.of("supplierId", lib2))), admin));
        String seen = ok(call(get(API + "/projects/" + project), admin)).path("dev").path("records").toString();
        assertTrue(seen.contains("IT甲供应商") && seen.contains("IT乙代理") && seen.contains("IT丙供应商"), "有权限照常显示：" + seen);

        loginWithResources("it_au_viewer", MENU_FOLLOWUP);
        String viewer = token("it_au_viewer");
        JsonNode records = ok(call(get(API + "/projects/" + project), viewer)).path("dev").path("records");
        assertEquals(3, records.size(), records.toString());
        String text = records.toString();
        assertFalse(text.contains("IT甲供应商") || text.contains("IT乙代理") || text.contains("IT丙供应商"),
                "没有「查看货源信息」看不到候选与供应商名称：" + text);
    }

    // ---------------------------------------------------------------- 造数

    private long orderItem(String soNo, int source, String model) {
        jdbc.update("insert into sales_order (tenant_id, so_no, status, source, sales_date) values (?, ?, 1, ?, curdate())",
                TENANT, soNo, source);
        long so = jdbc.queryForObject("select id from sales_order where tenant_id = ? and so_no = ?", Long.class, TENANT, soNo);
        jdbc.update("insert into sales_order_item (tenant_id, so_id, model, brand, quantity) values (?, ?, ?, 'ITAUBRAND', 2)",
                TENANT, so, model);
        return jdbc.queryForObject("select id from sales_order_item where so_id = ?", Long.class, so);
    }

    private long quoteWithDemand(String key, int status) {
        jdbc.update("insert into sourcing_task (tenant_id, task_code) values (?, 'ITAU-T1')", TENANT);
        long task = jdbc.queryForObject("select id from sourcing_task where tenant_id = ?", Long.class, TENANT);
        jdbc.update("insert into inquiry_item (tenant_id, brand, brand_id, original_model, model_key, quantity)"
                + " values (?, 'ITAUBRAND', ?, ?, ?, 1)", TENANT, brandId, key, key);
        long item = jdbc.queryForObject("select id from inquiry_item where tenant_id = ?", Long.class, TENANT);
        jdbc.update("insert into sourcing_quote (tenant_id, task_id, inquiry_item_id, brand, model, model_key, currency_code,"
                + " unit_price, no_stock, status, quoted_at) values (?, ?, ?, 'ITAUBRAND', ?, ?, 'CNY', 12.50, 0, ?, now())",
                TENANT, task, item, key, key, status);
        demand(key, 2, 3, 1);
        return jdbc.queryForObject("select id from sourcing_quote where tenant_id = ?", Long.class, TENANT);
    }

    private long demand(String key, int status, int priority, Integer coverage) {
        int cover = coverage == null ? 1 : coverage;
        jdbc.update("insert into sc_demand (tenant_id, brand_id, model_key, model, scope_key, status, priority, suggested_priority,"
                + " coverage_status, suggested_coverage) values (?, ?, ?, ?, ?, ?, ?, 1, ?, ?)",
                TENANT, brandId, key, key, "m:" + key, status, priority, cover, cover);
        return jdbc.queryForObject("select id from sc_demand where tenant_id = ? and scope_key = ?", Long.class, TENANT, "m:" + key);
    }

    private long supplier(String code) {
        jdbc.update("insert into supplier (tenant_id, supplier_code, name, status) values (?, ?, ?, 1)", TENANT, code, code);
        return jdbc.queryForObject("select id from supplier where supplier_code = ?", Long.class, code);
    }

    private void channel(long demand, long supplier, int role, int sort) {
        jdbc.update("insert into sc_coverage_channel (tenant_id, demand_id, supplier_id, role, sort_order) values (?, ?, ?, ?, ?)",
                TENANT, demand, supplier, role, sort);
    }

    private long project(String name, long... demands) {
        jdbc.update("insert into sc_gap_project (tenant_id, name, brand_id, category_id, goal, owner_id, due_date, status)"
                + " values (?, ?, ?, 0, 'x', 0, curdate(), 1)", TENANT, name, brandId);
        long id = jdbc.queryForObject("select id from sc_gap_project where tenant_id = ? and name = ?", Long.class, TENANT, name);
        for (long d : demands) {
            jdbc.update("insert into sc_gap_project_item (tenant_id, project_id, demand_id) values (?, ?, ?)", TENANT, id, d);
        }
        return id;
    }

    private List<Long> activeItems(long project) {
        return jdbc.queryForList("select demand_id from sc_gap_project_item where project_id = ? and deleted_at is null",
                Long.class, project);
    }

    private int logs(int sourceType) {
        return jdbc.queryForObject("select count(*) from sc_followup_log where tenant_id = ? and source_type = ?",
                Integer.class, TENANT, sourceType);
    }

    /** 本测试库里正在执行、读写 sc_demand 的其他连接数（重算被行锁挡住时停在这里） */
    private int lockWaits() {
        return jdbc.queryForObject("select count(*) from information_schema.processlist where db = database()"
                + " and command = 'Query' and id <> connection_id() and info like '%sc_demand%'", Integer.class);
    }

    private String token(String username) {
        String token = jwtUtils.generateToken(Map.of("tenantId", 0), username, 3600);
        redis.opsForValue().set(RedisKeyConstants.TOKEN_PREFIX + token, "1");
        return token;
    }

    private JsonNode call(MockHttpServletRequestBuilder request, String token) throws Exception {
        request.header("Authorization", "Bearer " + token);
        return objectMapper.readTree(mvc.perform(request).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder b, String body) {
        return b.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private JsonNode ok(JsonNode res) {
        assertEquals(0, res.path("code").asInt(), res.toString());
        return res.path("data");
    }

    private String write(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
