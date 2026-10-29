package com.zhul.erp.modules.aitask.dto;

import lombok.Data;

/** 容器内两个官方 CLI 的登录状态，不含账号信息 */
@Data
public class AiSubscriptionStatusVO {
    private boolean codexLoggedIn;
    private boolean claudeLoggedIn;
    private String codexLoginCommand = "docker compose exec ai-orchestrator codex login --device-auth";
    private String claudeLoginCommand = "docker compose exec ai-orchestrator claude auth login";
}
