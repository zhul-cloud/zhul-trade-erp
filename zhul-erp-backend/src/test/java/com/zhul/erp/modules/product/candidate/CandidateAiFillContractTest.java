package com.zhul.erp.modules.product.candidate;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhul.erp.modules.aitask.dto.AiTaskCallbackRequest;
import com.zhul.erp.modules.aitask.service.AiOrchestratorClient;
import com.zhul.erp.modules.aitask.service.AiTaskService;
import com.zhul.erp.modules.inquiry.InquiryContractSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** spec product/product-candidate「批量 AI 解析品类与品牌」 */
class CandidateAiFillContractTest extends InquiryContractSupport {

    private static final String CAND = "/api/v1/product/candidates";

    @Autowired private AiTaskService aiTaskService;
    /** 不连真实 AI 编排服务：提交后任务停在排队中，由测试手动回调 */
    @MockBean private AiOrchestratorClient orchestrator;

    private String admin;
    private long rockwell;
    private long plc;
    private long cpu;
    private long contactor;

    @BeforeEach
    void seed() {
        cleanup();
        seedAiConfig(0);
        when(orchestrator.submit(any(), anyLong(), any(), any())).thenAnswer(inv -> new CompletableFuture<>());
        loginAsAdmin("it_aifill_admin");
        admin = token("it_aifill_admin");
        jdbc.update("insert into product_brand (tenant_id, brand_name, status) values (0, 'ITRockwell', 1)");
        rockwell = jdbc.queryForObject("select id from product_brand where brand_name = 'ITRockwell'", Long.class);
        jdbc.update("insert into product_brand_alias (tenant_id, brand_id, alias, alias_key) values (0, ?, 'IT-Allen-Bradley', 'it-allen-bradley')", rockwell);
        plc = category("itaf-plc", null, "仝己控制器");
        cpu = category("itaf-cpu", plc, "仝己 CPU");
        contactor = category("itaf-contactor", null, "仝己接触器");
    }

    @AfterEach
    void cleanup() {
        jdbc.update("delete from ai_task where skill_id = 'candidate-classify'");
        jdbc.update("delete from product_candidate where mpn_raw like 'ITAF-%'");
        jdbc.update("delete from product_brand_alias where alias like 'IT-%'");
        jdbc.update("delete from product_brand where brand_name = 'ITRockwell'");
        jdbc.update("delete from product_category where category_code like 'itaf-%'");
        removeAiConfig(0);
    }

    private long category(String code, Long parent, String zh) {
        jdbc.update("insert into product_category (tenant_id, category_code, category_name, category_name_zh, parent_id, status) values (0, ?, ?, ?, ?, 1)",
                code, code, zh, parent);
        return jdbc.queryForObject("select id from product_category where tenant_id = 0 and category_code = ?", Long.class, code);
    }

    private long candidate(String mpn, Long brandId, Long categoryId, int status) {
        jdbc.update("insert into product_candidate (tenant_id, brand_id, brand_key, brand_text, mpn_raw, mpn_normalized, category_id, category_text, "
                + "status, level, source_count) values (0, ?, ?, 'Allen Bradley Co.', ?, ?, ?, '', ?, 1, 0)",
                brandId, brandId == null ? "allen bradley co." : "#" + brandId, mpn, mpn.toLowerCase().replace("-", ""), categoryId, status);
        return jdbc.queryForObject("select id from product_candidate where mpn_raw = ?", Long.class, mpn);
    }

    private JsonNode submit(String mode, List<Long> ids) throws Exception {
        return call(json(post(CAND + "/ai-fill"), write(Map.of("mode", mode, "ids", ids))), admin);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> sentItems() throws Exception {
        ArgumentCaptor<String> input = ArgumentCaptor.forClass(String.class);
        verify(orchestrator).submit(eq("candidate-classify"), anyLong(), input.capture(), any());
        clearInvocations(orchestrator);
        return (List<Map<String, Object>>) objectMapper.readValue(input.getValue(), Map.class).get("items");
    }

    private void callback(long task, List<Map<String, Object>> items) {
        AiTaskCallbackRequest cb = new AiTaskCallbackRequest();
        cb.setStatus("success");
        cb.setOutput(Map.of("items", items));
        aiTaskService.handleCallback(task, cb);
    }

    private static JsonNode fail(JsonNode res) {
        assertNotEquals(0, res.path("code").asInt(), res.toString());
        return res;
    }

    private Long col(String column, long id) {
        return jdbc.queryForObject("select " + column + " from product_candidate where id = ?", Long.class, id);
    }

    @Test
    void category_skipsDetailed_fillsMarked_doesNotOverwriteManualChange() throws Exception {
        long none = candidate("ITAF-1", rockwell, null, 1);
        long top = candidate("ITAF-2", rockwell, plc, 1);
        long detailed = candidate("ITAF-3", rockwell, cpu, 1);
        long archived = candidate("ITAF-4", rockwell, null, 2);
        long manual = candidate("ITAF-5", rockwell, null, 1);

        JsonNode vo = ok(submit("category", List.of(none, top, detailed, archived, manual)));
        assertEquals(3, vo.path("total").asInt());
        assertEquals(2, vo.path("skipped").asInt(), "已有细分品类、已审核的跳过");
        long task = vo.path("taskId").asLong();
        List<Map<String, Object>> sent = sentItems();
        assertEquals(List.of("c" + none + "#0", "c" + top + "#" + plc, "c" + manual + "#0"), sent.stream().map(m -> m.get("key")).toList());

        // 同一账号运行中不能再提交
        assertTrue(fail(submit("brand", List.of(none))).path("message").asText().contains("还没跑完"));

        // 运行期间人工选了品类
        jdbc.update("update product_candidate set category_id = ? where id = ?", contactor, manual);
        callback(task, List.of(
                Map.of("key", "c" + none + "#0", "categoryId", contactor, "brand", ""),
                Map.of("key", "c" + top + "#" + plc, "categoryId", cpu, "brand", ""),
                Map.of("key", "c" + manual + "#0", "categoryId", cpu, "brand", "")));

        assertEquals(contactor, col("category_id", none));
        assertEquals(1L, col("ai_category", none));
        assertEquals(cpu, col("category_id", top), "一级品类细化为细分");
        assertEquals(contactor, col("category_id", manual), "人工改过的不覆盖");
        assertEquals(0L, col("ai_category", manual));
        assertEquals(cpu, col("category_id", detailed));

        JsonNode status = ok(call(get(CAND + "/ai-fill/" + task), admin));
        assertEquals(3, status.path("status").asInt());
        assertEquals("category", status.path("mode").asText());
        assertEquals(2, status.path("filled").asInt());
        assertEquals(1, status.path("unchanged").asInt());

        JsonNode row = ok(call(get(CAND + "/" + none), admin));
        assertEquals(1, row.path("aiCategory").asInt());

        // 审核时把 AI 建议的品类改掉：标记去掉
        Map<String, Object> approve = Map.of("brandMode", "EXISTING", "brandId", rockwell, "categoryId", cpu, "lifecycleStatus", 1);
        ok(call(json(post(CAND + "/" + none + "/approve"), write(approve)), admin));
        assertEquals(0L, col("ai_category", none));
    }

    @Test
    void brand_matchesAliasOrMarksUnmatched_neverCreatesBrand() throws Exception {
        long alias = candidate("ITAF-11", null, contactor, 1);
        long unknown = candidate("ITAF-12", null, contactor, 1);
        long confirmed = candidate("ITAF-13", rockwell, contactor, 1);
        long brands = jdbc.queryForObject("select count(*) from product_brand", Long.class);

        JsonNode vo = ok(submit("brand", List.of(alias, unknown, confirmed)));
        assertEquals(2, vo.path("total").asInt());
        assertEquals(1, vo.path("skipped").asInt(), "品牌已确认的跳过");
        sentItems();
        callback(vo.path("taskId").asLong(), List.of(
                Map.of("key", "b" + alias + "#" + contactor, "categoryId", 0, "brand", "IT-Allen-Bradley"),
                Map.of("key", "b" + unknown + "#" + contactor, "categoryId", 0, "brand", "ITMitsubishi")));

        assertEquals(rockwell, col("brand_id", alias));
        assertEquals(1L, col("ai_brand", alias));
        assertNull(col("brand_id", unknown));
        assertEquals(2L, col("ai_brand", unknown));
        assertEquals("ITMitsubishi", jdbc.queryForObject("select ai_brand_name from product_candidate where id = ?", String.class, unknown));
        assertEquals(brands, jdbc.queryForObject("select count(*) from product_brand", Long.class), "不新建品牌");

        JsonNode status = ok(call(get(CAND + "/ai-fill/" + vo.path("taskId").asLong()), admin));
        assertEquals("brand", status.path("mode").asText());
        assertEquals(1, status.path("matched").asInt());
        assertEquals(1, status.path("unmatched").asInt());

        // 审核时换了品牌：AI 标记去掉
        Map<String, Object> approve = Map.of("brandMode", "EXISTING", "brandId", rockwell, "categoryId", contactor, "lifecycleStatus", 1);
        ok(call(json(post(CAND + "/" + alias + "/approve"), write(approve)), admin));
        assertEquals(1L, col("ai_brand", alias), "没改品牌，标记保留");
    }

    @Test
    void limitsAndAllSkipped() throws Exception {
        List<Long> tooMany = new ArrayList<>(LongStream.rangeClosed(1, 201).boxed().toList());
        assertTrue(fail(submit("category", tooMany)).path("message").asText().contains("200"));
        assertTrue(fail(submit("price", List.of(1L))).path("message").asText().contains("品类或品牌"));

        long detailed = candidate("ITAF-21", rockwell, cpu, 1);
        JsonNode vo = ok(submit("category", List.of(detailed)));
        assertTrue(vo.path("taskId").isNull() || vo.path("taskId").isMissingNode(), "全部跳过不产生任务");
        assertEquals(1, vo.path("skipped").asInt());

        // 没有审核权限的账号不能提交
        fail(call(json(post(CAND + "/ai-fill"), write(Map.of("mode", "brand", "ids", List.of(detailed)))), token("it_lin")));
    }
}
