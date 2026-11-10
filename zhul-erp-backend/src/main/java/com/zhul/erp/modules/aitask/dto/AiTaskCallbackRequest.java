package com.zhul.erp.modules.aitask.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * AI 编排服务 webhook 回调请求体，契约见
 * openspec/changes/add-inquiry-management/design.md 决策3：
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

    /** 分批解析的进度报告：output 为 {"done": 已完成批数, "total": 总批数}，不是最终结果 */
    public boolean isProgress() {
        return "progress".equalsIgnoreCase(status);
    }
}
