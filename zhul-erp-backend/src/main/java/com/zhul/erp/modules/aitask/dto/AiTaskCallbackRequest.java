package com.zhul.erp.modules.aitask.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * AI 编排服务 webhook 回调请求体，契约见
 * openspec/changes/archive/2026-10-03-add-inquiry-management/design.md 决策3：
 * {"status": "success"|"failed", "output": {...}, "error": null|string}
 */
@Data
public class AiTaskCallbackRequest {
    @NotBlank(message = "status不能为空")
    private String status;
    private Object output;
    private String error;
    /** 这次处理调用模型的用量；旧版编排服务、订阅方式没有 */
    private Usage usage;
    /** 实际完成调用的连接方式（用法:服务商，如 api:openai）；旧版编排服务没有 */
    private String connection;
    /** 执行中发生的切换；status 为 failover 时是这一次的切换，结果回调时是本任务的全部切换 */
    @Valid
    @Size(max = 20, message = "切换记录过多")
    private List<AiFailover> failovers;

    @Data
    public static class Usage {
        private String model;
        private Integer inputTokens;
        private Integer outputTokens;
        private Integer webSearches;
    }

    public boolean isSuccess() {
        return "success".equalsIgnoreCase(status);
    }

    /** 执行中刚发生的切换：不是最终结果，后端据此立刻让失败的连接方式暂停（openspec ai-connection-failover） */
    public boolean isFailover() {
        return "failover".equalsIgnoreCase(status);
    }

    /** 分批解析的进度报告：output 为 {"done": 已完成批数, "total": 总批数}，不是最终结果 */
    public boolean isProgress() {
        return "progress".equalsIgnoreCase(status);
    }
}
