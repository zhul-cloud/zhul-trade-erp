package com.zhul.erp.modules.aitask;

import com.zhul.erp.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** spec ai-platform/task-orchestration「记录 AI 调用用量」：回调接口带用量与不带用量 */
@AutoConfigureMockMvc
class AiTaskUsageContractTest extends IntegrationTestBase {

    private static final String SKILL = "it-usage-probe";

    @Autowired private MockMvc mvc;
    @Value("${zhul.ai-task.callback-token}") private String callbackToken;

    @AfterEach
    void cleanup() {
        jdbc.update("delete from ai_task where skill_id = ?", SKILL);
    }

    private long newTask() {
        jdbc.update("insert into ai_task (tenant_id, skill_id, status) values (0, ?, 2)", SKILL);
        return jdbc.queryForObject("select max(id) from ai_task where skill_id = ?", Long.class, SKILL);
    }

    private void callback(long id, String body) throws Exception {
        int status = mvc.perform(post("/api/v1/ai-tasks/" + id + "/callback")
                        .header("X-AI-Callback-Token", callbackToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getStatus();
        assertEquals(200, status);
    }

    @Test
    void callbackWithUsageSavesIt() throws Exception {
        long id = newTask();
        callback(id, """
                {"status":"success","output":{"ok":true},
                 "usage":{"model":"claude-sonnet-5-5","inputTokens":12000,"outputTokens":3500,"webSearches":4}}""");

        Map<String, Object> row = jdbc.queryForMap("select status, model, input_tokens, output_tokens, web_searches from ai_task where id = ?", id);
        assertEquals(3, ((Number) row.get("status")).intValue());
        assertEquals("claude-sonnet-5-5", row.get("model"));
        assertEquals(12000, ((Number) row.get("input_tokens")).intValue());
        assertEquals(3500, ((Number) row.get("output_tokens")).intValue());
        assertEquals(4, ((Number) row.get("web_searches")).intValue());
    }

    @Test
    void callbackWithoutUsageLeavesItEmpty() throws Exception {
        long id = newTask();
        callback(id, "{\"status\":\"success\",\"output\":{\"ok\":true}}");

        Map<String, Object> row = jdbc.queryForMap("select status, model, input_tokens, output_tokens, web_searches from ai_task where id = ?", id);
        assertEquals(3, ((Number) row.get("status")).intValue(), "没有用量的旧回调照常完成");
        assertEquals("", row.get("model"));
        assertNull(row.get("input_tokens"));
        assertNull(row.get("output_tokens"));
        assertNull(row.get("web_searches"));
    }
}
