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

    public boolean isSuccess() {
        return "success".equalsIgnoreCase(status);
    }
}
