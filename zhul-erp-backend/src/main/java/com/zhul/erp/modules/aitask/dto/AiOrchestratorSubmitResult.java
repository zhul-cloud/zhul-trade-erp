package com.zhul.erp.modules.aitask.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AiOrchestratorSubmitResult {
    private boolean accepted;
    private String jobId;
    private String errorMessage;

    public static AiOrchestratorSubmitResult accepted(String jobId) {
        return new AiOrchestratorSubmitResult(true, jobId, null);
    }

    public static AiOrchestratorSubmitResult rejected(String errorMessage) {
        return new AiOrchestratorSubmitResult(false, null, errorMessage);
    }
}
