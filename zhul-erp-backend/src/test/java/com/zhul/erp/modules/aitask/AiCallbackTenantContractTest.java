package com.zhul.erp.modules.aitask;

import com.zhul.erp.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * AI 回调不带用户令牌（没有租户上下文）：后端按任务所属租户处理结果（audit SEC-011）。
 * 走真实回调接口，验证租户拦截器启用后询盘仍能收到解析结果。
 */
@AutoConfigureMockMvc
class AiCallbackTenantContractTest extends IntegrationTestBase {

    private static final int TENANT = 99000093;

    @Autowired private MockMvc mvc;
    @Value("${zhul.ai-task.callback-token}") private String callbackToken;

    private long task;
    private long inquiry;

    @BeforeEach
    void data() {
        cleanup();
        jdbc.update("insert into ai_task (tenant_id, skill_id, status) values (?, 'inquiry-parse-and-split', 2)", TENANT);
        task = jdbc.queryForObject("select max(id) from ai_task where tenant_id = ?", Long.class, TENANT);
        jdbc.update("insert into customer_inquiry (tenant_id, inquiry_code, inquiry_date, quote_deadline, status, ai_task_id) values (?, 'ITCB-1', curdate(), curdate(), 2, ?)", TENANT, task);
        inquiry = jdbc.queryForObject("select id from customer_inquiry where inquiry_code = 'ITCB-1'", Long.class);
    }

    @AfterEach
    void cleanup() {
        jdbc.update("delete from customer_inquiry where tenant_id = ?", TENANT);
        jdbc.update("delete from ai_task where tenant_id = ?", TENANT);
    }

    @Test
    void callbackIsHandledUnderTheTasksTenant() throws Exception {
        int status = mvc.perform(post("/api/v1/ai-tasks/" + task + "/callback")
                        .header("X-AI-Callback-Token", callbackToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"failed\",\"error\":\"图片看不清\"}"))
                .andReturn().getResponse().getStatus();
        assertEquals(200, status);
        assertEquals(4, jdbc.queryForObject("select status from customer_inquiry where id = ?", Integer.class, inquiry),
                "解析失败回调把询盘改为解析失败");
        assertEquals(4, jdbc.queryForObject("select status from ai_task where id = ?", Integer.class, task));
    }
}
