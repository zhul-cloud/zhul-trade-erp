package com.zhul.erp.modules.aitask.controller;

import com.zhul.erp.common.result.Result;
import com.zhul.erp.common.result.ResultCode;
import com.zhul.erp.modules.aitask.dto.AiTaskCallbackRequest;
import com.zhul.erp.modules.aitask.service.AiTaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 任务的 webhook 回调：AI 编排服务处理完成后调这个地址，见 SecurityConfig 白名单与 design.md 决策3。
 * 创建任务只由业务模块（如询盘解析）在服务端拼好输入后调用 AiTaskService，不对外开放通用创建接口：
 * 客户端自拼的输入可让编排服务读取任意链接或本地文件（audit AI-003）。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ai-tasks")
@RequiredArgsConstructor
public class AiTaskController {

    private static final String CALLBACK_TOKEN_HEADER = "X-AI-Callback-Token";

    private final AiTaskService aiTaskService;

    @Value("${zhul.ai-task.callback-token}")
    private String callbackToken;

    /**
     * webhook 回调不使用用户态 JWT 鉴权（该端点已加入 SecurityConfig 白名单），
     * 改用共享密钥校验：请求头 X-AI-Callback-Token 必须与配置的
     * zhul.ai-task.callback-token 一致，不一致直接返回 401，不进入业务处理。
     */
    @PostMapping("/{taskId}/callback")
    public ResponseEntity<Result<Void>> callback(@PathVariable Long taskId,
                                                  @RequestHeader(value = CALLBACK_TOKEN_HEADER, required = false) String token,
                                                  @Valid @RequestBody AiTaskCallbackRequest req) {
        if (token == null || !token.equals(callbackToken)) {
            log.warn("AI任务回调鉴权失败，taskId={}", taskId);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Result.fail(ResultCode.UNAUTHORIZED));
        }
        aiTaskService.handleCallback(taskId, req);
        return ResponseEntity.ok(Result.ok());
    }
}
