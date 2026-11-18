package com.zhul.erp.modules.product.content;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhul.erp.modules.aitask.dto.AiTaskCallbackRequest;
import com.zhul.erp.modules.aitask.service.AiOrchestratorClient;
import com.zhul.erp.modules.aitask.service.AiTaskService;
import com.zhul.erp.modules.inquiry.InquiryContractSupport;
import com.zhul.erp.modules.product.content.service.ContentAiService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * spec product/product-content「AI 生成资料与营销内容」「AI 生成结果的确认与导入」：
 * 单个与批量发起、重复发起拦截、并发上限排队、结果回调、按权限分别写入（不自动审核）、取消、放弃、超期取消。
 * 编排服务不连真实 AI：提交后任务停在排队中，测试手动回调。
 */
class ContentAiContractTest extends InquiryContractSupport {

    private static final String TASKS = "/api/v1/product/content-tasks";
    private static final String PRODUCTS = "/api/v1/product/products";

    @MockBean private AiOrchestratorClient orchestrator;
    @Autowired private AiTaskService aiTaskService;
    @Autowired private ContentAiService aiService;

    private String admin;
    private long brand;
    private final List<Long> products = new ArrayList<>();

    @BeforeEach
    void seed() throws Exception {
        cleanup();
        seedAiConfig(0);
        when(orchestrator.submit(any(), anyLong(), any(), any())).thenAnswer(inv -> new CompletableFuture<>());
        jdbc.update("insert into product_brand (tenant_id, brand_name, status) values (0, 'ITCAI', 1)");
        brand = jdbc.queryForObject("select id from product_brand where brand_name = 'ITCAI'", Long.class);
        jdbc.update("insert into product_category (tenant_id, category_code, category_name, category_name_zh, status) "
                + "values (0, 'itcai-plc', 'PLC', '可编程控制器', 1)");
        long category = jdbc.queryForObject("select id from product_category where category_code = 'itcai-plc'", Long.class);
        loginAsAdmin("it_ai_admin");
        admin = token("it_ai_admin");
        products.clear();
        for (String mpn : new String[] {"AI-0001", "AI-0002", "AI-0003", "AI-0004"}) {
            products.add(ok(call(json(post(PRODUCTS), write(Map.of("brandId", brand, "categoryId", category, "mpnRaw", mpn))), admin)).path("id").asLong());
        }
    }

    @AfterEach
    void cleanup() {
        String ps = "(select id from product where brand_id in (select id from product_brand where brand_name like 'ITCAI%'))";
        for (String t : new String[] {"product_content_ai_job", "product_content_import", "product_content_task", "product_fact_task",
                "product_seo", "product_seo_faq", "product_faq", "product_platform_content", "product_platform_keyword",
                "product_keyword", "product_keyword_use", "product_fact_note", "product_specification", "product_application",
                "product_document", "product_locale", "product_logistics", "product_customs"}) {
            jdbc.update("delete from " + t + " where product_id in " + ps);
        }
        jdbc.update("delete from product where brand_id in (select id from product_brand where brand_name like 'ITCAI%')");
        jdbc.update("delete from product_brand where brand_name like 'ITCAI%'");
        jdbc.update("delete from product_category where category_code like 'itcai-%'");
        jdbc.update("delete from ai_task where skill_id = 'product-content'");
        removeAiConfig(0);
    }

    // ---------------------------------------------------------------- 造数据

    private JsonNode start(String token, Long... ids) throws Exception {
        return call(json(post(TASKS + "/ai/start"), write(Map.of("productIds", List.of(ids)))), token);
    }

    private Map<String, Object> job(long productId) {
        return jdbc.queryForMap("select id, status, ai_task_id, facts_file_name, marketing_file_name, fail_reason "
                + "from product_content_ai_job where product_id = ? order by id desc limit 1", productId);
    }

    private long jobId(long productId) {
        return ((Number) job(productId).get("id")).longValue();
    }

    private int status(long productId) {
        return ((Number) job(productId).get("status")).intValue();
    }

    private void callback(long productId, boolean success) {
        AiTaskCallbackRequest req = new AiTaskCallbackRequest();
        Long taskId = ((Number) job(productId).get("ai_task_id")).longValue();
        if (success) {
            String mpn = jdbc.queryForObject("select mpn_raw from product where id = ?", String.class, productId);
            req.setStatus("success");
            req.setOutput(Map.of("factsMarkdown", facts(mpn), "marketingMarkdown", marketing(mpn), "selfScore", 91,
                    "summary", "已核实型号页；海关编码为类目参考。"));
        } else {
            req.setStatus("failed");
            req.setError("AI 连接方式都不可用：Claude API 额度已用完");
        }
        aiTaskService.handleCallback(taskId, req);
    }

    private static String facts(String mpn) {
        return "---\nschema: 2\ntype: facts\nbrand: ITCAI\nmodel: " + mpn + "\nlang: zh\n---\n\n"
                + "## 一句话规格摘要\n测试用规格摘要\n\n## 技术问答\n1. Q: 是原装吗？\n   A: 是。\n";
    }

    private static String marketing(String mpn) {
        return "---\ntype: marketing\nplatform: site\nbrand: ITCAI\nmodel: " + mpn + "\nlang: en\n---\n\n"
                + "## 首屏定义块\nThe ITCAI " + mpn + " is a test product.\n\n## SEO 标题\n" + mpn + " | Fast Quote\n";
    }

    /** 确认请求：两份都确认（内容由服务端用保存的原文） */
    private Map<String, Object> confirmBody(long productId) {
        Map<String, Object> j = job(productId);
        List<Map<String, Object>> files = new ArrayList<>();
        for (String name : new String[] {(String) j.get("facts_file_name"), (String) j.get("marketing_file_name")}) {
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("fileName", name);
            f.put("content", "前端传来的内容不采用");
            files.add(f);
        }
        return Map.of("files", files);
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    /** 回调指定的两份原文（型号可以故意写错，造出不能写入的文件） */
    private void callbackWith(long productId, String factsMarkdown, String marketingMarkdown) {
        AiTaskCallbackRequest req = new AiTaskCallbackRequest();
        req.setStatus("success");
        req.setOutput(Map.of("factsMarkdown", factsMarkdown, "marketingMarkdown", marketingMarkdown, "selfScore", 88,
                "summary", "测试"));
        aiTaskService.handleCallback(((Number) job(productId).get("ai_task_id")).longValue(), req);
    }

    private List<Long> pageIds(String ai) throws Exception {
        List<Long> ids = new ArrayList<>();
        for (JsonNode r : ok(call(json(post(TASKS + "/facts/page"), write(Map.of("brandId", brand, "ai", ai))), admin))
                .path("records")) {
            ids.add(r.path("productId").asLong());
        }
        return ids;
    }

    // ---------------------------------------------------------------- 用例

    /** openspec content-ai-batch-hs：按 AI 状态筛选、批量确认 AI 结果（问题文件跳过、技术问答随资料包审核通过）、批量资料已核 */
    @Test
    void aiFilter_batchConfirm_batchVerify() throws Exception {
        long p1 = products.get(0);
        long p2 = products.get(1);
        long p3 = products.get(2);
        long p4 = products.get(3);
        ok(start(admin, p1, p2, p3));
        assertEquals(List.of(), pageIds("pending"));
        assertEquals(3, pageIds("running").size(), "排队或生成中");
        callbackWith(p1, facts("AI-0001"), marketing("AI-0001"));
        callbackWith(p2, facts("AI-0002"), marketing("AI-9999"));
        callbackWith(p3, facts("AI-9998"), marketing("AI-9999"));
        assertEquals(3, pageIds("pending").size(), "三个都待确认");
        assertEquals(List.of(), pageIds("failed"));

        JsonNode res = ok(call(json(post(TASKS + "/ai/confirm-batch"), write(Map.of("ids",
                List.of(jobId(p1), jobId(p2), jobId(p3))))), admin));
        assertEquals(1, res.path("done").asInt(), res.toString());
        assertEquals(1, res.path("partial").asInt(), res.toString());
        assertEquals(1, res.path("failed").asInt(), res.toString());
        assertEquals(2, res.path("approvedFaqs").asInt(), "p1、p2 的资料包各有 1 条技术问答，随资料包审核通过");
        assertEquals("AI-0001", res.path("items").get(0).path("label").asText());
        assertTrue(res.path("items").get(1).path("message").asText().contains("营销包"), res.toString());
        assertEquals(4, status(p1), "两份都写入，已导入");
        assertEquals(3, status(p2), "营销包没写入，仍待确认");
        assertEquals(3, status(p3));
        assertEquals(0, count("select count(*) from product_faq where product_id in (?, ?) and source = 3 and deleted_at is null",
                p1, p2), "技术问答不再待审核");
        assertNull(jdbc.queryForObject("select verified_at from product_fact_task where product_id = ?", Object.class, p1),
                "资料仍待核");

        // 批量资料已核：没有导入的也可以核对；已核对过的再提交跳过并说明原因
        JsonNode verified = ok(call(json(post(TASKS + "/facts/verify-batch"), write(Map.of("ids", List.of(p1, p2, p4)))), admin));
        assertEquals(3, verified.path("done").asInt(), verified.toString());
        assertEquals(1, count("select count(*) from product_fact_task where product_id = ? and verified_at is not null", p1));
        JsonNode again = ok(call(json(post(TASKS + "/facts/verify-batch"), write(Map.of("ids", List.of(p1)))), admin));
        assertEquals(1, again.path("failed").asInt());
        assertTrue(again.path("items").get(0).path("message").asText().contains("已核对"), again.toString());

        // 没有「商品内容维护」权限不能批量核对
        loginWithResources("it_ai_viewer", 100100);
        assertEquals(403, perform(json(post(TASKS + "/facts/verify-batch"), write(Map.of("ids", List.of(p1)))),
                token("it_ai_viewer")).getStatus());
    }

    @Test
    void batchQueuesBeyondThree_duplicateRejected_resultAndPermissionWrite() throws Exception {
        long p1 = products.get(0);
        long p2 = products.get(1);
        long p4 = products.get(3);
        JsonNode started = ok(start(admin, products.toArray(Long[]::new)));
        assertEquals(4, started.path("created").asInt());
        assertEquals(3, count("select count(*) from product_content_ai_job where status = 2 and product_id in (?, ?, ?, ?)",
                products.toArray()), "同时生成中不超过 3 个");
        assertEquals(1, status(p4), "第 4 个排队");
        assertTrue(fail(start(admin, p1)).path("message").asText().contains("正在生成"), "已在生成中不能重复发起");

        // 列表显示最近一次 AI 生成
        JsonNode rows = ok(call(json(post(TASKS + "/facts/page"), write(Map.of("brandId", brand))), admin)).path("records");
        for (JsonNode r : rows) {
            if (r.path("productId").asLong() == p4) {
                assertEquals(1, r.path("ai").path("status").asInt());
                assertEquals(0, r.path("ai").path("queueAhead").asInt());
            }
        }

        // 第 1 个完成：待确认，排队的补上
        callback(p1, true);
        assertEquals(3, status(p1));
        assertEquals(2, status(p4), "前一个结束后排队的开始生成");
        // 第 2 个失败：记原因，可以重新生成
        callback(p2, false);
        assertEquals(5, status(p2));
        assertTrue(((String) job(p2).get("fail_reason")).contains("都不可用"));

        JsonNode result = ok(call(get(TASKS + "/ai/" + jobId(p1)), admin));
        assertTrue(result.path("factsMarkdown").asText().contains("type: facts"));
        assertEquals(91, result.path("selfScore").asInt());
        assertTrue(result.path("canFacts").asBoolean() && result.path("canMarketing").asBoolean());

        // 只有「商品内容维护」：只写资料包，营销包提示无权限，记录仍待确认；不自动审核
        loginWithResources("it_ai_facts", 100100, 110202);
        JsonNode partial = ok(call(json(post(TASKS + "/ai/" + jobId(p1) + "/confirm"), write(confirmBody(p1))), token("it_ai_facts")));
        assertEquals(1, partial.path("succeeded").asInt(), partial.toString());
        assertTrue(partial.toString().contains("没有营销内容维护权限"));
        assertEquals(3, status(p1));
        assertEquals(1, count("select count(*) from product_content_import where product_id = ? and source = 2 "
                + "and package_type = 'facts'", p1), "上传记录注明 AI 生成");
        assertNull(jdbc.queryForObject("select verified_at from product_fact_task where product_id = ?", Object.class, p1),
                "资料写入后仍待核，不自动核对");
        assertEquals(0, count("select count(*) from product_content_import where product_id = ? and package_type = 'marketing'", p1));

        // 有权限的人再确认：资料包不重复写，营销包写入，记录变为已导入（测试账号只有一个，重新登录管理员）
        loginAsAdmin("it_ai_admin");
        admin = token("it_ai_admin");
        JsonNode rest = ok(call(json(post(TASKS + "/ai/" + jobId(p1) + "/confirm"), write(confirmBody(p1))), admin));
        assertEquals(1, rest.path("succeeded").asInt(), rest.toString());
        assertEquals(1, count("select count(*) from product_content_import where product_id = ? and package_type = 'facts'", p1),
                "资料包不重复写入");
        assertEquals(4, status(p1));
        assertTrue(fail(call(json(post(TASKS + "/ai/" + jobId(p1) + "/confirm"), write(confirmBody(p1))), admin))
                .path("message").asText().contains("待确认"));
    }

    @Test
    void cancelAbandonExpire_andLateResultIgnored() throws Exception {
        long p1 = products.get(0);
        ok(start(admin, p1));
        ok(call(post(TASKS + "/ai/" + jobId(p1) + "/cancel"), admin));
        assertEquals(6, status(p1));
        callback(p1, true);
        assertEquals(6, status(p1), "取消后迟到的结果不保存");

        ok(start(admin, p1));
        callback(p1, true);
        ok(call(post(TASKS + "/ai/" + jobId(p1) + "/abandon"), admin));
        assertEquals(6, status(p1));
        assertEquals(0, count("select count(*) from product_content_import where product_id = ?", p1), "放弃不写入");

        ok(start(admin, p1));
        callback(p1, true);
        jdbc.update("update product_content_ai_job set completed_at = now() - interval 31 day where id = ?", jobId(p1));
        aiService.expirePending();
        assertEquals(6, status(p1), "待确认超过 30 天自动取消");
        assertTrue(((String) job(p1).get("fail_reason")).contains("30 天"));
    }

    @Test
    void notConfigured_otherTenantHidden_viewerCannotStart() throws Exception {
        long p1 = products.get(0);
        removeAiConfig(0);
        fail(start(admin, p1));
        assertEquals(0, count("select count(*) from product_content_ai_job where product_id = ?", p1), "AI 未配置不建记录");

        seedAiConfig(0);
        jdbc.update("insert into product_content_ai_job (tenant_id, product_id, status) values (99, ?, 3)", p1);
        long other = jdbc.queryForObject("select id from product_content_ai_job where tenant_id = 99 and product_id = ?", Long.class, p1);
        assertTrue(fail(call(get(TASKS + "/ai/" + other), admin)).path("message").asText().contains("不存在"), "看不到其他公司的记录");
        ok(start(admin, p1));
        assertEquals(1, count("select count(*) from product_content_ai_job where tenant_id = 0 and product_id = ?", p1),
                "其他公司的记录不影响本公司发起");

        loginWithResources("it_ai_viewer", 100100);
        assertFalse(call(json(post(TASKS + "/ai/start"), write(Map.of("productIds", List.of(products.get(1))))),
                token("it_ai_viewer")).path("code").asInt() == 0, "没有维护权限不能发起");
    }

    private static JsonNode fail(JsonNode res) {
        assertTrue(res.path("code").asInt() != 0, "应当失败：" + res);
        return res;
    }
}
