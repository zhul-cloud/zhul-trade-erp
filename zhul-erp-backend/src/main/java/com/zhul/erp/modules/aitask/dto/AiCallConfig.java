package com.zhul.erp.modules.aitask.dto;

import com.zhul.erp.modules.aitask.constants.AiConfigConstants;
import lombok.Builder;
import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 发给 AI 编排服务的配置快照：默认值已补全，apiKey 为解密后的明文，只在内存中随一次请求使用。
 * toString() 只输出脱敏 Key，避免被日志或异常信息带出。
 */
@Getter
@Builder
public class AiCallConfig {
    private final String mode;
    private final String provider;
    private final String apiKey;
    private final String parseModel;
    private final String translateModel;
    private final String parseEffort;
    private final String translateEffort;
    private final int maxWebSearches;

    /** 请求体中的 ai 字段，键名与编排服务约定一致 */
    public Map<String, Object> toPayload() {
        Map<String, Object> m = new LinkedHashMap<>(16);
        m.put("mode", mode);
        m.put("provider", provider);
        m.put("api_key", apiKey == null ? "" : apiKey);
        m.put("parse_model", parseModel);
        m.put("translate_model", translateModel);
        m.put("parse_effort", parseEffort);
        m.put("translate_effort", translateEffort);
        m.put("max_web_searches", maxWebSearches);
        return m;
    }

    /** 脱敏中间部分的固定长度，不随 Key 实际长度变化，避免暴露长度 */
    private static final String MASK_MIDDLE = "••••••••••••••••";

    /** 脱敏：保留前 7 位与后 4 位，中间固定 16 个点（如 sk-ant-••••••••••••••••abcd）；不足 12 位时整串换成同样长度的点 */
    public static String mask(String key) {
        if (key == null || key.isEmpty()) {
            return "";
        }
        if (key.length() < 12) {
            return "•".repeat(27);
        }
        return key.substring(0, 7) + MASK_MIDDLE + key.substring(key.length() - 4);
    }

    public boolean isSubscription() {
        return AiConfigConstants.MODE_SUBSCRIPTION.equals(mode);
    }

    @Override
    public String toString() {
        return "AiCallConfig{mode=" + mode + ", provider=" + provider + ", apiKey=" + mask(apiKey)
                + ", parseModel=" + parseModel + ", translateModel=" + translateModel
                + ", parseEffort=" + parseEffort + ", translateEffort=" + translateEffort
                + ", maxWebSearches=" + maxWebSearches + "}";
    }
}
