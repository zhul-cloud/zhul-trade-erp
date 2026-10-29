package com.zhul.erp.modules.aitask.constants;

import java.util.List;
import java.util.Map;

/** 租户 AI 编排配置的取值与默认值，见 openspec ai-platform/tenant-ai-settings */
public final class AiConfigConstants {
    private AiConfigConstants() {
    }

    /** 平台租户：只有它能使用部署者个人订阅 */
    public static final int PLATFORM_TENANT_ID = 0;

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
}
