package com.zhul.erp.modules.aitask.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.aitask.dto.AiCallConfig;
import com.zhul.erp.modules.aitask.dto.AiConnectionTestVO;
import com.zhul.erp.modules.aitask.dto.AiModelListVO;
import com.zhul.erp.modules.aitask.dto.AiOrchestratorSubmitResult;
import com.zhul.erp.modules.aitask.dto.AiLoginStatus;
import com.zhul.erp.modules.aitask.service.AiOrchestratorClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 用 JDK 内建的 {@link HttpClient} 封装通用契约，不引入额外 HTTP 客户端依赖。
 * sendAsync 使用 HttpClient 内部线程池，调用方不阻塞等待响应，符合
 * design.md 决策3"不阻塞等待处理完成"的要求。
 * 请求体含明文 Key，任何日志与异常信息都不输出请求体。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiOrchestratorClientImpl implements AiOrchestratorClient {

    private static final String TOKEN_HEADER = "X-AI-Callback-Token";
    private static final Duration SYNC_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Value("${zhul.ai-task.orchestrator-base-url}")
    private String orchestratorBaseUrl;

    @Value("${zhul.ai-task.callback-base-url}")
    private String callbackBaseUrl;

    @Value("${zhul.ai-task.callback-token}")
    private String callbackToken;

    @Override
    public CompletableFuture<AiOrchestratorSubmitResult> submit(String skillId, Long taskId, String inputJson, AiCallConfig ai) {
        try {
            Map<String, Object> body = new HashMap<>(4);
            body.put("input", inputJson == null ? null : objectMapper.readTree(inputJson));
            body.put("callback_url", callbackBaseUrl + "/api/v1/ai-tasks/" + taskId + "/callback");
            body.put("ai", ai.toPayload());

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(orchestratorBaseUrl + "/skills/" + skillId + "/run"))
                    .header("Content-Type", "application/json")
                    .header(TOKEN_HEADER, callbackToken)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(this::toResult)
                    .exceptionally(ex -> {
                        log.warn("提交 AI 任务失败，taskId={}, skillId={}", taskId, skillId, ex);
                        return AiOrchestratorSubmitResult.rejected(ex.getMessage());
                    });
        } catch (JsonProcessingException e) {
            log.warn("构造 AI 任务提交请求失败，taskId={}, skillId={}", taskId, skillId, e);
            return CompletableFuture.completedFuture(AiOrchestratorSubmitResult.rejected(e.getMessage()));
        }
    }

    @Override
    public AiConnectionTestVO test(AiCallConfig ai) {
        JsonNode json = post("/test", Map.of("ai", ai.toPayload()));
        AiConnectionTestVO vo = new AiConnectionTestVO();
        vo.setOk(json.path("ok").asBoolean(false));
        List<AiConnectionTestVO.Item> items = new ArrayList<>(4);
        for (JsonNode n : json.path("items")) {
            AiConnectionTestVO.Item item = new AiConnectionTestVO.Item();
            item.setName(n.path("name").asText(""));
            item.setOk(n.path("ok").asBoolean(false));
            item.setMessage(n.path("message").asText(""));
            items.add(item);
        }
        vo.setItems(items);
        return vo;
    }

    @Override
    public AiModelListVO models(AiCallConfig ai) {
        JsonNode json = post("/models", Map.of("ai", ai.toPayload()));
        AiModelListVO vo = new AiModelListVO();
        List<AiModelListVO.Model> models = new ArrayList<>(32);
        for (JsonNode n : json.path("models")) {
            AiModelListVO.Model m = new AiModelListVO.Model();
            m.setId(n.path("id").asText(""));
            m.setDefaultModel(n.path("default").asBoolean(false));
            models.add(m);
        }
        vo.setModels(models);
        vo.setSource(json.path("source").asText("defaults"));
        vo.setError(json.hasNonNull("error") ? json.get("error").asText() : null);
        return vo;
    }

    @Override
    public AiLoginStatus status() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(orchestratorBaseUrl + "/status"))
                .header(TOKEN_HEADER, callbackToken)
                .timeout(SYNC_TIMEOUT)
                .GET()
                .build();
        JsonNode json = send(request, "/status");
        return new AiLoginStatus(json.path("claude").path("logged_in").asBoolean(false),
                json.path("codex").path("logged_in").asBoolean(false));
    }

    private JsonNode post(String path, Map<String, Object> body) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new BizException("构造 AI 编排服务请求失败", e);
        }
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(orchestratorBaseUrl + path))
                .header("Content-Type", "application/json")
                .header(TOKEN_HEADER, callbackToken)
                .timeout(SYNC_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();
        return send(request, path);
    }

    private JsonNode send(HttpRequest request, String path) {
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            log.warn("调用 AI 编排服务 {} 失败：{}", path, e.getClass().getSimpleName());
            throw new BizException("AI 编排服务不可用，请稍后重试", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException("调用 AI 编排服务被中断", e);
        }
        if (response.statusCode() == 401) {
            throw new BizException("AI 编排服务拒绝请求：服务之间的令牌不一致，请检查 AI_TASK_CALLBACK_TOKEN");
        }
        if (response.statusCode() != 200) {
            throw new BizException("AI 编排服务返回状态码 " + response.statusCode());
        }
        try {
            return objectMapper.readTree(response.body());
        } catch (JsonProcessingException e) {
            throw new BizException("解析 AI 编排服务响应失败", e);
        }
    }

    private AiOrchestratorSubmitResult toResult(HttpResponse<String> response) {
        if (response.statusCode() != 202) {
            return AiOrchestratorSubmitResult.rejected("AI编排服务返回非202状态码: " + response.statusCode());
        }
        try {
            JsonNode json = objectMapper.readTree(response.body());
            String jobId = json.has("job_id") ? json.get("job_id").asText() : null;
            return AiOrchestratorSubmitResult.accepted(jobId);
        } catch (JsonProcessingException e) {
            return AiOrchestratorSubmitResult.rejected("解析AI编排服务受理响应失败: " + e.getMessage());
        }
    }
}
