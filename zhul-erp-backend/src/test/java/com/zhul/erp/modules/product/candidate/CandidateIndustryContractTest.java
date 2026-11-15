package com.zhul.erp.modules.product.candidate;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.aitask.dto.AiTaskCallbackRequest;
import com.zhul.erp.modules.aitask.service.AiOrchestratorClient;
import com.zhul.erp.modules.aitask.service.AiTaskService;
import com.zhul.erp.modules.aitask.support.AiTaskKeys;
import com.zhul.erp.modules.product.constants.ProductConstants;
import com.zhul.erp.support.TenantContractSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * audit BUS-065：商品候选的品类只能用本租户行业的（spec product/industry「商品候选对品类的识别只用本行业」）。
 * AI 解析品类发给 AI 的清单、回调写入、批量修改、审核建档都按租户行业校验。
 */
class CandidateIndustryContractTest extends TenantContractSupport {

    private static final String IC = "INDUSTRIAL_CONTROL";
    private static final String OTHER_INDUSTRY = "IT_OTHER_INDUSTRY";
    private static final int TENANT = 99000651;
    private static final String CAND = "/api/v1/product/candidates";

    @Autowired private AiTaskService aiTaskService;
    /** 不连真实 AI 编排服务：提交后任务停在排队中，由测试手动回调 */
    @MockBean private AiOrchestratorClient orchestrator;

    private long plc;
    private long cpu;
    private long valve;
    private long ball;
    private long brand;
    private String t;

    @BeforeEach
    void setUp() {
        cleanup();
        seedAiConfig(TENANT);
        when(orchestrator.submit(any(), anyLong(), any(), any())).thenAnswer(inv -> new CompletableFuture<>());
        jdbc.update("insert into tenant_package (id, name, menu_ids) select ?, 'IT候选行业套餐', JSON_ARRAYAGG(id) from resource", TENANT);
        jdbc.update("insert into tenant (id, code, name, industry_code, package_id) values (?, 'ITCI1', 'IT 候选行业租户', ?, ?)",
                TENANT, IC, TENANT);
        plc = category("itci_plc", null, IC);
        cpu = category("itci_cpu", plc, "");
        valve = category("itci_valve", null, OTHER_INDUSTRY);
        ball = category("itci_ball", valve, "");
        jdbc.update("insert into product_brand (tenant_id, brand_name, status) values (0, 'ITCI-SIE', 1)");
        brand = jdbc.queryForObject("select id from product_brand where brand_name = 'ITCI-SIE'", Long.class);
        jdbc.update("insert into product_brand_industry (brand_id, industry_code) values (?, ?)", brand, IC);
        redis.delete(List.of(ProductConstants.CACHE_KEY_BRAND_OPTIONS, ProductConstants.CACHE_KEY_CATEGORY_OPTIONS));
        testUserTenant = TENANT;
        loginAsAdmin("it_ci_boss");
        t = token("it_ci_boss", TENANT);
    }

    @AfterEach
    void cleanup() {
        TenantContext.clear();
        jdbc.update("delete from ai_task where skill_id = 'candidate-classify'");
        jdbc.update("delete from product where mpn_raw like 'ITCIX-%'");
        jdbc.update("delete from product_candidate_source where candidate_id in (select id from product_candidate where mpn_raw like 'ITCIX-%')");
        jdbc.update("delete from product_candidate where mpn_raw like 'ITCIX-%'");
        jdbc.update("delete from product_brand_industry where brand_id in (select id from product_brand where brand_name like 'ITCI-%')");
        jdbc.update("delete from product_brand where brand_name like 'ITCI-%'");
        jdbc.update("delete from product_category where category_code like 'itci\\_%'");
        jdbc.update("delete from tenant where id = ?", TENANT);
        jdbc.update("delete from tenant_package where id = ?", TENANT);
        removeAiConfig(TENANT);
        redis.delete(List.of(ProductConstants.CACHE_KEY_BRAND_OPTIONS, ProductConstants.CACHE_KEY_CATEGORY_OPTIONS));
    }

    private long category(String code, Long parentId, String industry) {
        jdbc.update("insert into product_category (tenant_id, category_code, category_name, category_name_zh, parent_id, industry_code, status) "
                + "values (0, ?, ?, ?, ?, ?, 1)", code, code, "仝" + code, parentId, industry);
        return jdbc.queryForObject("select id from product_category where category_code = ?", Long.class, code);
    }

    /** 待审核候选，来源属于本租户 */
    private long candidate(String mpn, Long categoryId) {
        jdbc.update("insert into product_candidate (tenant_id, brand_id, brand_key, brand_text, mpn_raw, mpn_normalized, category_id, category_text, "
                + "status, level, source_count) values (0, ?, ?, 'ITCI-SIE', ?, ?, ?, '', 1, 1, 1)",
                brand, "#" + brand, mpn, mpn.toLowerCase().replace("-", ""), categoryId);
        long id = jdbc.queryForObject("select id from product_candidate where mpn_raw = ?", Long.class, mpn);
        jdbc.update("insert into product_candidate_source (tenant_id, candidate_id, source_type) values (?, ?, 1)", TENANT, id);
        return id;
    }

    private Long col(String column, long id) {
        return jdbc.queryForObject("select " + column + " from product_candidate where id = ?", Long.class, id);
    }

    @Test
    @SuppressWarnings("unchecked")
    void aiFill_categoryListAndCallbackLimitedToTenantIndustry() throws Exception {
        long a = candidate("ITCIX-1", null);
        long b = candidate("ITCIX-2", null);
        JsonNode vo = ok(call(json(post(CAND + "/ai-fill"), write(Map.of("mode", "category", "ids", List.of(a, b)))), t));
        long task = vo.path("taskId").asLong();
        ArgumentCaptor<String> input = ArgumentCaptor.forClass(String.class);
        verify(orchestrator).submit(eq("candidate-classify"), anyLong(), input.capture(), any());
        clearInvocations(orchestrator);
        List<Long> sent = ((List<Map<String, Object>>) objectMapper.readValue(input.getValue(), Map.class).get("categories"))
                .stream().map(m -> ((Number) m.get("id")).longValue()).toList();
        assertTrue(sent.containsAll(List.of(plc, cpu)), sent.toString());
        assertFalse(sent.contains(valve) || sent.contains(ball), "其他行业的品类不发给 AI：" + sent);

        AiTaskCallbackRequest cb = new AiTaskCallbackRequest();
        cb.setStatus("success");
        cb.setOutput(Map.of("items", List.of(
                Map.of("key", AiTaskKeys.sign("c" + a + "#0"), "categoryId", cpu, "brand", ""),
                Map.of("key", AiTaskKeys.sign("c" + b + "#0"), "categoryId", ball, "brand", ""))));
        TenantContext.runAs(TENANT, () -> aiTaskService.handleCallback(task, cb));
        assertEquals(cpu, col("category_id", a));
        assertNull(col("category_id", b), "AI 给出其他行业的品类按未识别处理");
    }

    @Test
    void batchEditAndApprove_rejectOtherIndustryCategory() throws Exception {
        long a = candidate("ITCIX-3", null);
        fail(call(json(post(CAND + "/batch-edit"), write(Map.of("ids", List.of(a), "categoryId", ball))), t));
        assertNull(col("category_id", a), "批量修改不能选其他行业的品类");

        fail(call(json(post(CAND + "/" + a + "/approve"), write(Map.of("categoryId", ball))), t));
        assertEquals(1L, col("status", a), "审核建档不能用其他行业的品类");

        // 候选上已有其他行业的品类（历史数据）：批量通过跳过
        jdbc.update("update product_candidate set category_id = ? where id = ?", ball, a);
        JsonNode batch = ok(call(json(post(CAND + "/batch-approve"), write(Map.of("ids", List.of(a)))), t));
        assertEquals(0, batch.path("approved").asInt(), batch.toString());
        assertEquals(1L, col("status", a));

        // 本行业的品类正常建档
        jdbc.update("update product_candidate set category_id = ? where id = ?", cpu, a);
        assertEquals(1, ok(call(json(post(CAND + "/batch-approve"), write(Map.of("ids", List.of(a)))), t)).path("approved").asInt());
    }
}
