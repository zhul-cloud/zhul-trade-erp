package com.zhul.erp.modules.aitask.service;

import com.zhul.erp.modules.aitask.dto.AiCallConfig;
import com.zhul.erp.modules.aitask.dto.AiConnectionTestVO;
import com.zhul.erp.modules.aitask.dto.AiModelListVO;
import com.zhul.erp.modules.aitask.dto.AiOrchestratorSubmitResult;
import com.zhul.erp.modules.aitask.dto.AiSubscriptionStatusVO;

import java.util.concurrent.CompletableFuture;

/**
 * Java 后端与外部 AI 编排服务之间的通用 HTTP 契约封装，见
 * openspec/changes/add-inquiry-management/design.md 决策3：
 * POST {AI服务}/skills/{skill_id}/run，只处理 202 受理响应，不阻塞等待处理完成，
 * 实际结果由 AI 编排服务通过 webhook 回调 {@link AiTaskService#handleCallback} 异步返回。
 * 每次请求都带共享令牌；租户 AI 配置随任务放在请求体 ai 字段（Key 为明文，只在这次请求中出现）。
 */
public interface AiOrchestratorClient {
    CompletableFuture<AiOrchestratorSubmitResult> submit(String skillId, Long taskId, String inputJson, AiCallConfig ai);

    /** 同步测试连接：API 方式查询两个模型，订阅方式检查登录 */
    AiConnectionTestVO test(AiCallConfig ai);

    /** 同步获取模型下拉列表 */
    AiModelListVO models(AiCallConfig ai);

    /** 同步查询容器内两个官方 CLI 的登录状态 */
    AiSubscriptionStatusVO status();
}
