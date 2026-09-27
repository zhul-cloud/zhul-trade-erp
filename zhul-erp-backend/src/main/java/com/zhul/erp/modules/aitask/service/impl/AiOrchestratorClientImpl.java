package com.zhul.erp.modules.aitask.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.modules.aitask.dto.AiOrchestratorSubmitResult;
import com.zhul.erp.modules.aitask.service.AiOrchestratorClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 用 JDK 内建的 {@link HttpClient} 封装通用契约，不引入额外 HTTP 客户端依赖。
 * sendAsync 使用 HttpClient 内部线程池，调用方不阻塞等待响应，符合
 * design.md 决策3"不阻塞等待处理完成"的要求。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiOrchestratorClientImpl implements AiOrchestratorClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Value("${zhul.ai-task.orchestrator-base-url}")
    private String orchestratorBaseUrl;

    @Value("${zhul.ai-task.callback-base-url}")
    private String callbackBaseUrl;

    @Value("${zhul.ai-task.callback-token}")
    private String callbackToken;

    @Override
    public CompletableFuture<AiOrchestratorSubmitResult> submit(String skillId, Long taskId, String inputJson) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("input", inputJson == null ? null : objectMapper.readTree(inputJson));
            body.put("callback_url", callbackBaseUrl + "/api/v1/ai-tasks/" + taskId + "/callback");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(orchestratorBaseUrl + "/skills/" + skillId + "/run"))
                    .header("Content-Type", "application/json")
                    .header("X-AI-Callback-Token", callbackToken)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(this::toResult)
                    .exceptionally(ex -> {
                        log.warn("提交 AI 任务失败，taskId={}, skillId={}", taskId, skillId, ex);
                        return AiOrchestratorSubmitResult.rejected(ex.getMessage());
                    });
        } catch (Exception e) {
            log.warn("构造 AI 任务提交请求失败，taskId={}, skillId={}", taskId, skillId, e);
            return CompletableFuture.completedFuture(AiOrchestratorSubmitResult.rejected(e.getMessage()));
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
        } catch (Exception e) {
            return AiOrchestratorSubmitResult.rejected("解析AI编排服务受理响应失败: " + e.getMessage());
        }
    }
}
