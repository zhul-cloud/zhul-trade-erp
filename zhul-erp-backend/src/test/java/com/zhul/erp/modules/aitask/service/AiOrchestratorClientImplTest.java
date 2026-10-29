package com.zhul.erp.modules.aitask.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.aitask.dto.AiCallConfig;
import com.zhul.erp.modules.aitask.dto.AiConnectionTestVO;
import com.zhul.erp.modules.aitask.dto.AiModelListVO;
import com.zhul.erp.modules.aitask.dto.AiOrchestratorSubmitResult;
import com.zhul.erp.modules.aitask.dto.AiSubscriptionStatusVO;
import com.zhul.erp.modules.aitask.service.impl.AiOrchestratorClientImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 用本地 HTTP 服务核对发给编排服务的请求：带令牌、带 ai 字段，失败时日志与异常不含 Key */
@ExtendWith(OutputCaptureExtension.class)
class AiOrchestratorClientImplTest {

    private static final String KEY = "sk-secret-client-test-4321";
    private static final AiCallConfig CONFIG = AiCallConfig.builder().mode("api").provider("openai").apiKey(KEY)
            .parseModel("gpt-6.1-sol").translateModel("gpt-6-luna").parseEffort("high").translateEffort("medium")
            .maxWebSearches(7).build();

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, String> bodies = new ConcurrentHashMap<>();
    private final Map<String, String> tokens = new ConcurrentHashMap<>();
    private HttpServer server;
    private AiOrchestratorClientImpl client;

    private void respond(HttpExchange ex, int status, String body) throws IOException {
        bodies.put(ex.getRequestURI().getPath(), new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        tokens.put(ex.getRequestURI().getPath(), String.valueOf(ex.getRequestHeaders().getFirst("X-AI-Callback-Token")));
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/skills/inquiry-parse-and-split/run", ex -> respond(ex, 202, "{\"job_id\":\"j1\"}"));
        server.createContext("/test", ex -> respond(ex, 200,
                "{\"ok\":false,\"items\":[{\"name\":\"API Key\",\"ok\":true,\"message\":\"有效\"},"
                        + "{\"name\":\"解析模型 gpt-6.1-sol\",\"ok\":false,\"message\":\"模型不存在\"}]}"));
        server.createContext("/models", ex -> respond(ex, 200,
                "{\"models\":[{\"id\":\"gpt-6.1-sol\",\"default\":true},{\"id\":\"gpt-6-luna\"}],\"source\":\"provider\"}"));
        server.createContext("/status", ex -> respond(ex, 200,
                "{\"codex\":{\"logged_in\":true},\"claude\":{\"logged_in\":false}}"));
        server.createContext("/unauthorized/test", ex -> respond(ex, 401, "{}"));
        server.start();
        client = newClient("http://127.0.0.1:" + server.getAddress().getPort());
    }

    private AiOrchestratorClientImpl newClient(String baseUrl) {
        AiOrchestratorClientImpl c = new AiOrchestratorClientImpl(HttpClient.newHttpClient(), objectMapper);
        ReflectionTestUtils.setField(c, "orchestratorBaseUrl", baseUrl);
        ReflectionTestUtils.setField(c, "callbackBaseUrl", "http://backend:8080");
        ReflectionTestUtils.setField(c, "callbackToken", "it-token");
        return c;
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void submitCarriesAiConfigAndToken() throws Exception {
        AiOrchestratorSubmitResult r = client.submit("inquiry-parse-and-split", 9L, "{\"rawContent\":\"x\"}", CONFIG).get();
        assertTrue(r.isAccepted());
        JsonNode body = objectMapper.readTree(bodies.get("/skills/inquiry-parse-and-split/run"));
        assertEquals("it-token", tokens.get("/skills/inquiry-parse-and-split/run"));
        assertEquals("http://backend:8080/api/v1/ai-tasks/9/callback", body.path("callback_url").asText());
        JsonNode ai = body.path("ai");
        assertEquals("openai", ai.path("provider").asText());
        assertEquals(KEY, ai.path("api_key").asText());
        assertEquals("high", ai.path("parse_effort").asText());
        assertEquals(7, ai.path("max_web_searches").asInt());
    }

    @Test
    void syncCallsParseResponses() {
        AiConnectionTestVO test = client.test(CONFIG);
        assertFalse(test.isOk());
        assertEquals(2, test.getItems().size());
        assertEquals("模型不存在", test.getItems().get(1).getMessage());
        assertEquals("it-token", tokens.get("/test"));

        AiModelListVO models = client.models(CONFIG);
        assertEquals("provider", models.getSource());
        assertTrue(models.getModels().get(0).isDefaultModel());
        assertFalse(models.getModels().get(1).isDefaultModel());

        AiSubscriptionStatusVO status = client.status();
        assertTrue(status.isCodexLoggedIn());
        assertFalse(status.isClaudeLoggedIn());
    }

    @Test
    void failuresNeverExposeKey(CapturedOutput output) throws Exception {
        BizException unauthorized = assertThrows(BizException.class,
                () -> newClient("http://127.0.0.1:" + server.getAddress().getPort() + "/unauthorized").test(CONFIG));
        assertTrue(unauthorized.getMessage().contains("令牌"));

        server.stop(0);
        BizException down = assertThrows(BizException.class, () -> client.test(CONFIG));
        AiOrchestratorSubmitResult rejected = client.submit("inquiry-parse-and-split", 10L, "{}", CONFIG).get();
        assertFalse(rejected.isAccepted());

        for (String text : new String[] {unauthorized.getMessage(), down.getMessage(), rejected.getErrorMessage(),
                output.getAll(), CONFIG.toString()}) {
            assertFalse(text != null && text.contains(KEY), "不应包含 Key：" + text);
        }
        assertTrue(CONFIG.toString().contains("sk-secr••••••••••••••••4321"));
    }
}
