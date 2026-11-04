package com.zhul.erp.modules.crm.followup;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.common.constants.RedisKeyConstants;
import com.zhul.erp.common.utils.JwtUtils;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.aitask.dto.AiTaskCallbackRequest;
import com.zhul.erp.modules.aitask.service.AiOrchestratorClient;
import com.zhul.erp.modules.aitask.service.AiTaskService;
import com.zhul.erp.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 客户跟进的 AI（tasks 7.3）：AI 整理成功 / 失败 / 没配置 AI、原文截断，英文回复同步等待。
 * 编排服务用模拟对象代替；回调走真实的回调接口（没有登录、没有租户上下文），或在另一个线程里执行，
 * 验证回调按任务所属租户处理（参照 AttachmentTranscodeTenantContractTest）。
 */
@AutoConfigureMockMvc
class FollowupAiContractTest extends IntegrationTestBase {

    private static final String BASE = "/api/v1/followups";
    private static final long SELF = 99000002L;
    private static final long C1 = 99100011L;

    @Autowired private MockMvc mvc;
    @Autowired private JwtUtils jwtUtils;
    @Autowired private StringRedisTemplate redis;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AiTaskService aiTaskService;
    @MockBean private AiOrchestratorClient orchestrator;
    @Value("${zhul.ai-task.callback-token}") private String callbackToken;

    private String admin;

    @BeforeEach
    void setUp() {
        cleanup();
        seedAiConfig(0);
        loginAsAdmin("it_fu_ai");
        admin = token("it_fu_ai");
        jdbc.update("insert into customer (id, tenant_id, customer_code, name, country, owner_id, city, website)"
                + " values (?, 0, 'CUAI', 'ADORA', 'Germany', ?, '', 'https://old.example.com')", C1, SELF);
        when(orchestrator.submit(any(), anyLong(), any(), any())).thenReturn(new CompletableFuture<>());
    }

    @AfterEach
    void tearDown() {
        cleanup();
        removeAiConfig(0);
    }

    private void cleanup() {
        jdbc.update("delete from ai_task where tenant_id = 0 and skill_id like 'followup-%'");
        jdbc.update("delete from followup_item where tenant_id = 0");
        jdbc.update("delete from customer where tenant_id = 0");
    }

    private String token(String username) {
        String token = jwtUtils.generateToken(Map.of("tenantId", 0), username, 3600);
        redis.opsForValue().set(RedisKeyConstants.TOKEN_PREFIX + token, "1");
        return token;
    }

    private JsonNode call(MockHttpServletRequestBuilder request) throws Exception {
        request.header("Authorization", "Bearer " + admin);
        return objectMapper.readTree(mvc.perform(request).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder b, String body) {
        return b.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static JsonNode ok(JsonNode res) {
        assertEquals(0, res.path("code").asInt(), res.toString());
        return res.path("data");
    }

    private void callback(long taskId, String body) throws Exception {
        int status = mvc.perform(post("/api/v1/ai-tasks/" + taskId + "/callback")
                        .header("X-AI-Callback-Token", callbackToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getStatus();
        assertEquals(200, status);
    }

    private long submitExtract(String raw) throws Exception {
        return ok(call(json(post(BASE + "/extract"), objectMapper.writeValueAsString(Map.of("customerId", C1, "rawContent", raw)))))
                .path("taskId").asLong();
    }

    @Test
    void extract_success_resultCleanedToLegalValues() throws Exception {
        long taskId = submitExtract("customer: competitor quoted USD 760 refurbished, we take 10 new at USD 780, 15 days?");
        ArgumentCaptor<String> input = ArgumentCaptor.forClass(String.class);
        verify(orchestrator, timeout(5000)).submit(eq("followup-extract"), eq(taskId), input.capture(), any());
        JsonNode sent = objectMapper.readTree(input.getValue());
        assertEquals("ADORA", sent.path("customer").path("name").asText());
        assertEquals(10, sent.path("blockers").size());
        assertTrue(sent.path("profileFields").toString().contains("city"), "城市为空，可以补");
        assertFalse(sent.path("profileFields").toString().contains("website"), "官网已有值，不让 AI 建议");

        assertEquals(1, ok(call(get(BASE + "/extract/" + taskId))).path("status").asInt(), "还在排队");
        callback(taskId, """
                {"status":"success","output":{"summary":"客户嫌贵，愿意按 USD 780 拿 10 台全新","situation":["feedback","negotiating","no_reply"],
                 "blocker":"WEATHER","nextActions":[{"text":"向采购确认 15 天交期","days":0},{"text":"回复 USD 780 成交方案","days":1},{"text":"多余","days":2}],
                 "profileSuggestions":[{"field":"city","value":"Berlin","evidence":"we are in Berlin"},{"field":"currency","value":"EUR","evidence":""}],
                 "truncated":false}}""");
        JsonNode r = ok(call(get(BASE + "/extract/" + taskId).param("customerId", String.valueOf(C1))));
        assertEquals(3, r.path("status").asInt());
        JsonNode res = r.path("result");
        assertEquals(3, res.path("situation").asInt(), "反馈 + 谈条件，去掉矛盾的没回复");
        assertEquals("", res.path("blockerCode").asText(), "卡点只能取 SOP 卡点码");
        assertEquals(2, res.path("nextActions").size());
        assertEquals(LocalDate.now().plusDays(1).toString(), res.path("nextActions").get(1).path("date").asText());
        assertEquals(1, res.path("profileSuggestions").size(), "只能取可补的档案字段");
        assertEquals("城市", res.path("profileSuggestions").get(0).path("label").asText());
        assertTrue(res.path("profileSuggestions").get(0).path("checked").asBoolean(), "目标字段为空，默认勾选");
        assertEquals("Berlin", objectMapper.readTree(jdbc.queryForObject("select output from ai_task where id = ?", String.class, taskId))
                .path("profileSuggestions").get(0).path("value").asText(), "清洗后的结果写回任务");
        assertEquals("", jdbc.queryForObject("select city from customer where id = ?", String.class, C1), "只做建议，不改档案");
    }

    @Test
    void extract_failed() throws Exception {
        long taskId = submitExtract("hello");
        callback(taskId, "{\"status\":\"failed\",\"error\":\"AI 处理超过 600 秒未完成\"}");
        JsonNode r = ok(call(get(BASE + "/extract/" + taskId)));
        assertEquals(4, r.path("status").asInt());
        assertEquals("AI 处理超过 600 秒未完成", r.path("errorMessage").asText());
        assertTrue(r.path("result").isNull() || r.path("result").isMissingNode());
    }

    @Test
    void extract_noAiConfig_returnsErrorCode() throws Exception {
        removeAiConfig(0);
        JsonNode res = call(json(post(BASE + "/extract"), "{\"customerId\":" + C1 + ",\"rawContent\":\"hi\"}"));
        assertEquals("AI_NOT_CONFIGURED", res.path("data").path("errorCode").asText(), res.toString());
        assertEquals(0, jdbc.queryForObject("select count(*) from ai_task where tenant_id = 0 and skill_id like 'followup-%'", Integer.class),
                "不产生任务");
        JsonNode reply = call(json(post(BASE + "/reply-draft"), "{\"customerId\":" + C1 + "}"));
        assertEquals("AI_NOT_CONFIGURED", reply.path("data").path("errorCode").asText(), reply.toString());
    }

    @Test
    void extract_keepsLast20000Chars() throws Exception {
        long taskId = submitExtract("OLD" + "x".repeat(20000) + "NEWEST");
        ArgumentCaptor<String> input = ArgumentCaptor.forClass(String.class);
        verify(orchestrator, timeout(5000)).submit(eq("followup-extract"), eq(taskId), input.capture(), any());
        String raw = objectMapper.readTree(input.getValue()).path("rawContent").asText();
        assertEquals(20000, raw.length());
        assertTrue(raw.endsWith("NEWEST"));
    }

    @Test
    void replyDraft_waitsForCallbackFromAnotherThread() throws Exception {
        AtomicReference<Integer> tenantInCallback = new AtomicReference<>(-1);
        doAnswer(inv -> {
            long taskId = inv.getArgument(1);
            // 另一个线程、没有租户上下文，等同于编排服务打回来的回调
            Thread t = new Thread(() -> {
                tenantInCallback.set(TenantContext.getTenantId());
                AiTaskCallbackRequest req = new AiTaskCallbackRequest();
                req.setStatus("success");
                req.setOutput(Map.of("reply", "Hi, could you share your target price?", "intent", "问客户目标价"));
                aiTaskService.handleCallback(taskId, req);
            });
            t.start();
            return new CompletableFuture<>();
        }).when(orchestrator).submit(eq("followup-reply"), anyLong(), any(), any());

        JsonNode r = ok(call(json(post(BASE + "/reply-draft"), "{\"customerId\":" + C1 + ",\"blockerCode\":\"PRICE\",\"note\":\"问目标价\"}")));
        assertEquals("Hi, could you share your target price?", r.path("reply").asText());
        assertEquals("问客户目标价", r.path("intent").asText());
        assertNull(tenantInCallback.get(), "回调线程本身没有租户上下文");
        ArgumentCaptor<String> input = ArgumentCaptor.forClass(String.class);
        verify(orchestrator).submit(eq("followup-reply"), anyLong(), input.capture(), any());
        JsonNode sent = objectMapper.readTree(input.getValue());
        assertEquals("价格", sent.path("blocker").path("name").asText());
        assertFalse(sent.path("blocker").path("playbook").isEmpty(), "带上 SOP 应对要点");
        assertEquals("问目标价", sent.path("note").asText());
    }

    @Test
    void replyDraft_failed() throws Exception {
        doAnswer(inv -> {
            long taskId = inv.getArgument(1);
            new Thread(() -> {
                AiTaskCallbackRequest req = new AiTaskCallbackRequest();
                req.setStatus("failed");
                req.setError("boom");
                aiTaskService.handleCallback(taskId, req);
            }).start();
            return new CompletableFuture<>();
        }).when(orchestrator).submit(eq("followup-reply"), anyLong(), any(), any());
        JsonNode res = call(json(post(BASE + "/reply-draft"), "{\"customerId\":" + C1 + "}"));
        assertEquals("AI_FAILED", res.path("data").path("errorCode").asText(), res.toString());
    }
}
