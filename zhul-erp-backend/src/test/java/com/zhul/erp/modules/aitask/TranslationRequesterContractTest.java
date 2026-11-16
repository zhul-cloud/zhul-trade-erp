package com.zhul.erp.modules.aitask;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.common.constants.RedisKeyConstants;
import com.zhul.erp.common.utils.JwtUtils;
import com.zhul.erp.modules.aitask.translation.CustomerNameTranslations;
import com.zhul.erp.modules.aitask.translation.DescriptionTranslations;
import com.zhul.erp.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * audit AI-014：当前用户按用户名找不到用户档案（user_basic）时，提交翻译后任务里的提交人存为 0，
 * 查结果时当前用户解析为空，两者对不上，提示「翻译任务不存在」。「生成英文描述」与客户名「AI 翻译」都要能查到。
 */
@AutoConfigureMockMvc
class TranslationRequesterContractTest extends IntegrationTestBase {

    private static final long ACCOUNT_USER = 99000002L;

    @Autowired private MockMvc mvc;
    @Autowired private JwtUtils jwtUtils;
    @Autowired private StringRedisTemplate redis;
    @Autowired private ObjectMapper objectMapper;

    private String token;

    @BeforeEach
    void setUp() {
        cleanup();
        seedAiConfig(0);
        loginAsAdmin("it_ai014_admin");
        // 有登录账号，按用户名找不到用户档案
        jdbc.update("delete from user_basic where id = ?", ACCOUNT_USER);
        token = jwtUtils.generateToken(Map.of("tenantId", 0), "it_ai014_admin", 3600);
        redis.opsForValue().set(RedisKeyConstants.TOKEN_PREFIX + token, "1");
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    private void cleanup() {
        jdbc.update("delete from ai_task where tenant_id = 0 and skill_id in (?, ?)", CustomerNameTranslations.SKILL_ID,
                DescriptionTranslations.SKILL_ID);
        removeAiConfig(0);
    }

    private JsonNode call(MockHttpServletRequestBuilder r) throws Exception {
        r.header("Authorization", "Bearer " + token);
        return objectMapper.readTree(mvc.perform(r).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private void canReadOwnTask(String base, String body) throws Exception {
        JsonNode started = call(post(base).contentType(MediaType.APPLICATION_JSON).content(body));
        assertEquals(0, started.path("code").asInt(), started.toString());
        long taskId = started.path("data").path("taskId").asLong();
        JsonNode status = call(get(base + "/" + taskId));
        assertEquals(0, status.path("code").asInt(), status.toString());
        assertEquals(taskId, status.path("data").path("taskId").asLong());
    }

    @Test
    void accountWithoutUserProfile_canReadOwnDescriptionTranslation() throws Exception {
        canReadOwnTask("/api/v1/translations/item-descriptions", "{\"items\":[{\"key\":\"1\",\"text\":\"西门子 PLC 模块\"}]}");
    }

    @Test
    void accountWithoutUserProfile_canReadOwnNameTranslation() throws Exception {
        canReadOwnTask("/api/v1/masterdata/customers/name-translation", "{\"name\":\"ABC Automation GmbH\"}");
    }
}
