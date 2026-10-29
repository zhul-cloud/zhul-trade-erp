package com.zhul.erp.modules.aitask.dto;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiCallConfigTest {

    @Test
    void maskKeepsFirstSevenAndLastFourWithFixedMiddle() {
        assertEquals("sk-ant-••••••••••••••••abcd", AiCallConfig.mask("sk-ant-api03-xyz-abcd"));
        assertEquals("abcdefg••••••••••••••••2345", AiCallConfig.mask("abcdefg12345"));
        // 中间长度固定，不随 Key 长度变化
        assertEquals(AiCallConfig.mask("sk-ant-api03-xyz-abcd").length(),
                AiCallConfig.mask("sk-ant-api03-a-much-longer-key-value-abcd").length());
        assertEquals("•••••••••••••••••••••••••••", AiCallConfig.mask("abcd5678xyz"));
        assertEquals("", AiCallConfig.mask(""));
        assertEquals("", AiCallConfig.mask(null));
    }

    @Test
    void toStringNeverContainsKeyButPayloadDoes() {
        AiCallConfig c = AiCallConfig.builder().mode("api").provider("claude").apiKey("sk-ant-secret-value-9999")
                .parseModel("claude-opus-5-5").translateModel("claude-haiku-5-5").parseEffort("medium")
                .translateEffort("medium").maxWebSearches(20).build();
        assertFalse(c.toString().contains("sk-ant-secret-value-9999"));
        assertTrue(c.toString().contains("sk-ant-••••••••••••••••9999"));
        Map<String, Object> payload = c.toPayload();
        assertEquals("sk-ant-secret-value-9999", payload.get("api_key"));
        assertEquals(20, payload.get("max_web_searches"));
        assertFalse(c.isSubscription());
    }
}
