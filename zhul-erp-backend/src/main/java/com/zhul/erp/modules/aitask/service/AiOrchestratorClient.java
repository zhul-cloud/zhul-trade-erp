package com.zhul.erp.modules.aitask.service;

import com.zhul.erp.modules.aitask.dto.AiOrchestratorSubmitResult;

import java.util.concurrent.CompletableFuture;

/**
 * Java 后端与外部 AI 编排服务之间的通用 HTTP 契约封装，见
 * openspec/changes/add-inquiry-management/design.md 决策3：
 * POST {AI服务}/skills/{skill_id}/run，只处理 202 受理响应，不阻塞等待处理完成，
 * 实际结果由 AI 编排服务通过 webhook 回调 {@link AiTaskService#handleCallback} 异步返回。
 */
public interface AiOrchestratorClient {
    CompletableFuture<AiOrchestratorSubmitResult> submit(String skillId, Long taskId, String inputJson);
}
