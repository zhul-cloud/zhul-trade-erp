package com.zhul.erp.modules.inquiry.sourcing;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhul.erp.modules.aitask.service.AiOrchestratorClient;
import com.zhul.erp.modules.inquiry.InquiryContractSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * spec inquiry/sourcing-quote「AI 识别报价」（change sourcing-ai-quote-fill）。
 * 编排服务用模拟对象代替；结果走真实的回调接口（没有登录、没有租户上下文），验证回调按任务所属租户处理并按租户字典清洗。
 */
class QuoteAiFillContractTest extends InquiryContractSupport {

    private static final String SKILL = "sourcing-quote-parse";

    @MockBean private AiOrchestratorClient orchestrator;
    @Value("${zhul.ai-task.callback-token}") private String callbackToken;

    private String admin;

    @AfterEach
    void clearAi() {
        jdbc.update("delete from ai_task where tenant_id = 0 and skill_id = ?", SKILL);
        removeAiConfig(0);
    }

    private long taskOf(String brand, String category, String... models) throws Exception {
        long c = customer("AI Fill Co", "Kenya");
        long id = ok(call(json(post(INQ), "{\"customerId\":" + c + ",\"source\":1,\"rawContent\":\"rfq\"}"), admin)).path("id").asLong();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String m : models) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("brand", brand);
            r.put("category", category);
            r.put("confirmedModel", m);
            r.put("quantity", 2);
            rows.add(r);
        }
        ok(call(json(post(INQ + "/" + id + "/confirm"), write(Map.of("rows", rows))), admin));
        long task = jdbc.queryForObject("select id from sourcing_task where customer_inquiry_id = ?", Long.class, id);
        ok(call(json(post(BOARD + "/assign"), "{\"taskIds\":[" + task + "],\"assigneeId\":" + BUYER_LIN + "}"), admin));
        return task;
    }

    private void login() {
        loginAsAdmin("it_ai_fill_admin");
        admin = token("it_ai_fill_admin");
    }

    private JsonNode start(long task, String text, String token) throws Exception {
        return call(json(post(MY + "/" + task + "/paste-ai"), write(Map.of("text", text))), token);
    }

    private JsonNode result(long task, long aiTask, String token) throws Exception {
        return call(get(MY + "/" + task + "/paste-ai/" + aiTask), token);
    }

    private void callback(long aiTask, Object output) throws Exception {
        int status = mvc.perform(post("/api/v1/ai-tasks/" + aiTask + "/callback")
                        .header("X-AI-Callback-Token", callbackToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(write(Map.of("status", "success", "output", output))))
                .andReturn().getResponse().getStatus();
        assertEquals(200, status);
    }

    private int enabledCode(String dictType) {
        return jdbc.queryForObject("select cast(item_value as signed) from dict_item where dict_type = ? and status = 1"
                + " and tenant_id = 0 and deleted_at is null order by sort_order limit 1", Integer.class, dictType);
    }

    private static Map<String, Object> row(Object itemId, Object price, boolean taxed, Object rate, Object cond, Object lead, String confidence) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("itemId", itemId);
        r.put("raw", "raw " + itemId);
        r.put("unitPrice", price);
        r.put("taxIncluded", taxed);
        r.put("taxRate", rate);
        r.put("itemCondition", cond);
        r.put("leadTime", lead);
        r.put("note", "");
        r.put("confidence", confidence);
        return r;
    }

    @Test
    void withoutAi_onlyRuleParsing() throws Exception {
        login();
        long task = taskOf("Mitsubishi", "伺服与运动", "MR-J4-70B");
        removeAiConfig(0);
        String lin = token("it_lin");
        assertFalse(ok(call(get(MY + "/paste-ai/enabled"), lin)).asBoolean());
        JsonNode res = start(task, "MR-J4-70B 2850", lin);
        assertEquals("AI_NOT_CONFIGURED", res.path("data").path("errorCode").asText(), res.toString());
    }

    @Test
    void aiParse_onlyTaskModels_cleanedByTenantDicts_otherTasksListed() throws Exception {
        login();
        long task = taskOf("Mitsubishi", "伺服与运动", "MR-J4-70B", "HG-KR73", "MR-J4-200A");
        long other = taskOf("Mitsubishi", "变频器", "FR-E740-0.75K-CHT", "FR-D740-3.7K-CHT");
        List<Long> items = jdbc.queryForList("select id from inquiry_item where sourcing_task_id = ? order by line_no", Long.class, task);
        long foreignItem = jdbc.queryForObject("select id from inquiry_item where sourcing_task_id = ? limit 1", Long.class, other);
        seedAiConfig(0);
        when(orchestrator.submit(any(), anyLong(), any(), any())).thenReturn(new CompletableFuture<>());
        String lin = token("it_lin");
        assertTrue(ok(call(get(MY + "/paste-ai/enabled"), lin)).asBoolean());

        assertTrue(start(task, "x".repeat(20001), lin).path("message").asText().contains("20000"), "原文上限");
        assertEquals("询价任务不存在", start(task, "MR-J4-70B 2850", token("it_jiang")).path("message").asText(), "他人任务");

        JsonNode started = ok(start(task, "MR-J4-70B 驱动 含税 2850 现货\nFR-E740-0.75K 780", lin));
        long aiTask = started.path("aiTaskId").asLong();
        assertEquals("RUNNING", started.path("status").asText());
        ArgumentCaptor<String> input = ArgumentCaptor.forClass(String.class);
        verify(orchestrator, timeout(5000)).submit(eq(SKILL), eq(aiTask), input.capture(), any());
        JsonNode sent = objectMapper.readTree(input.getValue());
        assertEquals(3, sent.path("items").size());
        assertEquals("MR-J4-70B", sent.path("items").get(0).path("model").asText());
        assertTrue(sent.path("otherTasks").toString().contains("FR-E740-0.75K-CHT"), "本人其他待处理任务的型号");
        assertTrue(sent.path("taxRates").size() > 0);
        assertEquals("RUNNING", ok(result(task, aiTask, lin)).path("status").asText());

        int cond = enabledCode("inquiry_item_condition");
        int lead = enabledCode("inquiry_lead_time");
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("rows", List.of(
                row(items.get(0), 2850, true, 13, cond, lead, "check"),
                row(items.get(1), 1650, false, null, cond, 999, "sure"),
                row(items.get(2), -5, false, null, null, null, "sure"),
                row(foreignItem, 780, false, null, null, null, "sure")));
        output.put("otherModels", List.of(Map.of("taskId", other, "model", "FR-E740-0.75K-CHT"),
                Map.of("taskId", other, "model", "FR-D740-3.7K-CHT"), Map.of("taskId", 1, "model", "X")));
        output.put("unknownModels", List.of("E2E-X5ME1"));
        output.put("common", "");
        callback(aiTask, output);

        JsonNode done = ok(result(task, aiTask, lin));
        assertEquals("DONE", done.path("status").asText());
        JsonNode rows = done.path("preview").path("rows");
        assertEquals(3, rows.size(), "不属于当前任务的明细 id 不进预览");
        assertEquals("MR-J4-70B", rows.get(0).path("model").asText());
        assertEquals(0, rows.get(0).path("unitPrice").decimalValue().compareTo(new java.math.BigDecimal("2850.00")));
        assertEquals("2522.12", rows.get(0).path("unitPriceExTax").asText(), "2850 ÷ 1.13，两位小数 HALF_UP");
        assertEquals(13, rows.get(0).path("taxRate").asInt());
        assertEquals("CHECK", rows.get(0).path("confidence").asText());
        assertEquals(lead, rows.get(0).path("leadTime").asInt());
        assertFalse(rows.get(1).path("leadTime").isNumber(), "非字典值置空");
        assertEquals("CHECK", rows.get(1).path("confidence").asText(), "非字典值改为请核对");
        assertEquals(1650.0, rows.get(1).path("unitPriceExTax").asDouble(), 0.0001, "不含税时等于单价");
        assertFalse(rows.get(2).path("unitPrice").isNumber(), "负价置空");
        assertEquals("NO_PRICE", rows.get(2).path("status").asText());
        assertEquals("CHECK", rows.get(2).path("confidence").asText());
        JsonNode others = done.path("preview").path("otherTasks");
        assertEquals(1, others.size(), "只列本人的待处理任务");
        assertEquals(other, others.get(0).path("taskId").asLong());
        assertEquals(2, others.get(0).path("modelCount").asInt());
        assertEquals("E2E-X5ME1", done.path("preview").path("unknownModels").get(0).asText());
        assertEquals(0, jdbc.queryForObject("select count(*) from sourcing_quote where task_id = ?", Integer.class, task), "识别不落库");
        assertTrue(result(task, aiTask, token("it_jiang")).path("message").asText().contains("不存在"), "只能看自己的识别");
    }

    @Test
    void oneAtATime_cancel_andTimeout() throws Exception {
        login();
        long task = taskOf("Mitsubishi", "伺服与运动", "MR-J4-70B");
        seedAiConfig(0);
        when(orchestrator.submit(any(), anyLong(), any(), any())).thenReturn(new CompletableFuture<>());
        String lin = token("it_lin");

        long first = ok(start(task, "MR-J4-70B 2850", lin)).path("aiTaskId").asLong();
        long second = ok(start(task, "MR-J4-70B 2800", lin)).path("aiTaskId").asLong();
        JsonNode replaced = ok(result(task, first, lin));
        assertEquals("FAILED", replaced.path("status").asText(), "同一人同时只跑一个识别");
        assertEquals("已开始新的识别", replaced.path("errorMessage").asText());

        ok(call(post(MY + "/" + task + "/paste-ai/" + second + "/cancel"), lin));
        assertEquals("已取消", ok(result(task, second, lin)).path("errorMessage").asText());
        // 取消后迟到的回调被忽略
        callback(second, Map.of("rows", List.of(), "otherModels", List.of(), "unknownModels", List.of(), "common", ""));
        assertEquals("FAILED", ok(result(task, second, lin)).path("status").asText());

        long third = ok(start(task, "MR-J4-70B 2800", lin)).path("aiTaskId").asLong();
        jdbc.update("update ai_task set create_time = date_sub(now(), interval 100 second) where id = ?", third);
        JsonNode timeout = ok(result(task, third, lin));
        assertEquals("FAILED", timeout.path("status").asText());
        assertEquals("AI 识别超时", timeout.path("errorMessage").asText());

        // 服务商报错等技术原因不直接给采购看
        long fourth = ok(start(task, "MR-J4-70B 2800", lin)).path("aiTaskId").asLong();
        mvc.perform(post("/api/v1/ai-tasks/" + fourth + "/callback").header("X-AI-Callback-Token", callbackToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"failed\",\"error\":\"Anthropic 401 invalid x-api-key\"}"));
        assertEquals("AI 识别失败", ok(result(task, fourth, lin)).path("errorMessage").asText());
    }
}
