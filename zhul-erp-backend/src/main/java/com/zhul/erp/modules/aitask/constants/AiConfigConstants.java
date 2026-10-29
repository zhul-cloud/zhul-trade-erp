package com.zhul.erp.modules.aitask.constants;

import java.util.List;
import java.util.Map;

/** 租户 AI 编排配置的取值与默认值，见 openspec ai-platform/tenant-ai-settings */
public final class AiConfigConstants {
    private AiConfigConstants() {
    }

    /** 平台租户：始终可用平台订阅，订阅设置以它的为准 */
    public static final int PLATFORM_TENANT_ID = 0;

    /** 按钮资源「使用平台订阅」：套餐包含它的公司可以使用平台订阅（V1.3.22） */
    public static final int RESOURCE_USE_SUBSCRIPTION = 110215;
    /** 「使用平台订阅」所在的菜单：AI 设置 */
    public static final int RESOURCE_AI_SETTINGS = 100118;

    public static final String MODE_API = "api";
    public static final String MODE_SUBSCRIPTION = "subscription";

    public static final String PROVIDER_CLAUDE = "claude";
    public static final String PROVIDER_OPENAI = "openai";
    public static final List<String> PROVIDERS = List.of(PROVIDER_CLAUDE, PROVIDER_OPENAI);

    public static final List<String> EFFORTS = List.of("low", "medium", "high", "xhigh", "max");
    public static final String DEFAULT_EFFORT = "medium";

    public static final int DEFAULT_MAX_WEB_SEARCHES = 20;
    public static final int MIN_WEB_SEARCHES = 1;
    public static final int MAX_WEB_SEARCHES = 100;

    /** 各服务商的默认模型：parse 询盘解析，translate 型号描述翻译与联系人名音译；与编排服务 DEFAULT_MODELS 一致 */
    public static final Map<String, Map<String, String>> DEFAULT_MODELS = Map.of(
            PROVIDER_CLAUDE, Map.of("parse", "claude-opus-5-5", "translate", "claude-haiku-5-5"),
            PROVIDER_OPENAI, Map.of("parse", "gpt-6.1-sol", "translate", "gpt-6-luna"));

    public static final String NOT_CONFIGURED_MESSAGE = "AI 未配置，请在「业务设置 → AI 设置」中填写";
    /** 选用的平台订阅不可用时的提示，不说明原因 */
    public static final String UNAVAILABLE_MESSAGE = "AI 暂不可用，请在「业务设置 → AI 设置」中改选其他方式";

    /** 容器内登录命令：provider → 命令，只返回给平台 */
    public static final Map<String, String> LOGIN_COMMANDS = Map.of(
            PROVIDER_CLAUDE, "docker compose exec ai-orchestrator claude auth login",
            PROVIDER_OPENAI, "docker compose exec ai-orchestrator codex login --device-auth");
}
