package com.zhul.erp.modules.product.candidate;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhul.erp.modules.aitask.dto.AiTaskCallbackRequest;
import com.zhul.erp.modules.aitask.service.AiOrchestratorClient;
import com.zhul.erp.modules.aitask.service.AiTaskService;
import com.zhul.erp.modules.aitask.support.AiTaskKeys;
import com.zhul.erp.modules.sales.SalesContractSupport;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * openspec unlinked-docs-enhance：待关联清单显示单号、标为不是商品（可恢复，单据不改）、批量 AI 识别型号与批量采纳。
 * 历史明细直接插在一张采购订单上，型号都以 ULM 开头，用例前后只清理这些数据。
 */
class UnlinkedMarkContractTest extends SalesContractSupport {

    private static final String CAND = "/api/v1/product/candidates";

    @Autowired private AiTaskService aiTaskService;
    /** 不连真实 AI 编排服务：提交后任务停在排队中，由测试手动回调 */
    @MockBean private AiOrchestratorClient orchestrator;

    private long po;

    @BeforeEach
    void seedUlm() throws Exception {
        cleanupUlm();
        seedAiConfig(0);
        when(orchestrator.submit(any(), anyLong(), any(), any())).thenAnswer(inv -> new CompletableFuture<>());
        long c = customer("ULM Trading", "Brazil");
        long q = quotation(c, 2, "USD", null, l("ULM-SEED-1", 1, "10", "20"));
        long pi = sentPi(quotationItemIds(q), null);
        ok(uploadSlip(pi, "20.00", java.time.LocalDate.now().toString(), admin));
        long so = ok(call(post(PI + "/" + pi + "/convert"), admin)).path("id").asLong();
        po = generate(supplier("ULM Supplier", "预付"), requirementIds(so), admin);
        // 上线前的历史行：一行写法不规范的型号，一行运费
        jdbc.update("insert into purchase_order_item (tenant_id, po_id, model, brand, description, quantity, unit_price, amount, sort_order) "
                + "values (0, ?, 'ulm 6es7 214', 'ULMBrand', '', 1, 1, 1, 8), (0, ?, 'ULM运费 DHL', '', '', 1, 30, 30, 9)", po, po);
    }

    @AfterEach
    void cleanupUlm() {
        jdbc.update("delete from doc_unlinked_mark where group_key like '%ulm%'");
        jdbc.update("delete from ai_task where skill_id = 'unlinked-model-parse'");
        jdbc.update("delete from product_candidate_source where candidate_id in (select id from product_candidate where mpn_normalized like 'ulm%')");
        jdbc.update("delete from product_candidate where mpn_normalized like 'ulm%'");
        jdbc.update("delete from product where mpn_normalized like 'ulm%'");
        removeAiConfig(0);
    }

    private JsonNode group(String keyword, boolean marked) throws Exception {
        JsonNode list = ok(call(get(CAND + "/unlinked").param("keyword", keyword).param("marked", String.valueOf(marked)), admin));
        return list.path("total").asInt() == 0 ? null : list.path("records").get(0);
    }

    private JsonNode keys(String path, List<String> keys) throws Exception {
        return call(json(post(CAND + "/unlinked/" + path), write(Map.of("keys", keys))), admin);
    }

    @Test
    void docRefs_ignoreAndRestore_documentsUnchanged() throws Exception {
        JsonNode fee = group("运费", false);
        assertNotNull(fee);
        assertEquals(1, fee.path("docCount").asInt());
        JsonNode ref = fee.path("docRefs").get(0);
        assertEquals("PO", ref.path("type").asText());
        assertEquals(po, ref.path("id").asLong());
        String poNo = jdbc.queryForObject("select po_no from purchase_order where id = ?", String.class, po);
        assertEquals(poNo == null ? "草稿" : poNo, ref.path("no").asText(), "采购订单草稿还没有编号时显示「草稿」");

        String key = fee.path("key").asText();
        assertEquals(1, ok(keys("ignore", List.of(key))).asInt());
        assertEquals(null, group("运费", false), "标为不是商品后移出清单");
        JsonNode marked = group("运费", true);
        assertTrue(marked.path("ignored").asBoolean());
        assertEquals("it_sales_admin", marked.path("ignoredBy").asText());
        assertEquals(1, jdbc.queryForObject("select count(*) from purchase_order_item where po_id = ? and sort_order = 9 "
                + "and product_id is null and deleted_at is null and amount = 30", Integer.class, po), "单据明细原样保留");

        assertEquals(1, ok(keys("restore", List.of(key))).asInt());
        assertNotNull(group("运费", false), "恢复后回到清单");
    }

    @Test
    @SuppressWarnings("unchecked")
    void aiSuggestions_onlySuggest_adoptLinksModelGroups() throws Exception {
        String model = group("6es7", false).path("key").asText();
        String fee = group("运费", false).path("key").asText();
        JsonNode vo = ok(keys("ai", List.of(model, fee, "nobrand|notexist")));
        assertEquals(2, vo.path("total").asInt());
        assertEquals(1, vo.path("skipped").asInt(), "不在清单里的组跳过");
        ArgumentCaptor<String> input = ArgumentCaptor.forClass(String.class);
        verify(orchestrator).submit(eq("unlinked-model-parse"), anyLong(), input.capture(), any());
        List<Map<String, Object>> sent = (List<Map<String, Object>>) objectMapper.readValue(input.getValue(), Map.class).get("items");
        assertEquals(List.of(AiTaskKeys.sign(model), AiTaskKeys.sign(fee)), sent.stream().map(m -> m.get("key")).toList());

        AiTaskCallbackRequest cb = new AiTaskCallbackRequest();
        cb.setStatus("success");
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(Map.of("key", AiTaskKeys.sign(model), "kind", "model", "brand", "ULMBrand", "model", "ULM-6ES7214-1AG40"));
        items.add(Map.of("key", AiTaskKeys.sign(fee), "kind", "fee", "brand", "", "model", ""));
        items.add(Map.of("key", AiTaskKeys.sign("x|forged") + "0", "kind", "model", "brand", "X", "model", "X-1"));
        cb.setOutput(Map.of("items", items));
        long task = vo.path("taskId").asLong();
        aiTaskService.handleCallback(task, cb);
        JsonNode status = ok(call(get(CAND + "/unlinked/ai/" + task), admin));
        assertEquals(1, status.path("models").asInt());
        assertEquals(1, status.path("fees").asInt());

        JsonNode m = group("6es7", false);
        assertEquals(1, m.path("aiKind").asInt());
        assertEquals("ULM-6ES7214-1AG40", m.path("aiModel").asText());
        assertEquals(2, group("运费", false).path("aiKind").asInt(), "AI 判为费用也不自动标记，仍在清单");
        assertEquals(1, jdbc.queryForObject("select count(*) from purchase_order_item where po_id = ? and sort_order = 8 "
                + "and product_id is null", Integer.class, po), "AI 只给建议，不自动关联");

        JsonNode adopted = ok(keys("adopt", List.of(model, fee)));
        assertEquals(1, adopted.path("adopted").asInt());
        assertEquals(1, adopted.path("lines").asInt());
        assertEquals(fee, adopted.path("failures").get(0).path("key").asText());
        assertEquals("没有 AI 建议的型号", adopted.path("failures").get(0).path("message").asText());
        assertNotNull(jdbc.queryForObject("select product_id from purchase_order_item where po_id = ? and sort_order = 8", Long.class, po));
    }
}
