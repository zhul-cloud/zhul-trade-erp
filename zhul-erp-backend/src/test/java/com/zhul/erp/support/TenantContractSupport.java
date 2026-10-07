package com.zhul.erp.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.common.constants.RedisKeyConstants;
import com.zhul.erp.common.utils.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 按租户调用接口的契约测试基类：账号仍建在平台（tenant 0，用于权限判定），
 * 令牌里的 tenantId 决定接口看到的租户，用来验证租户隔离与「租户覆盖、否则用平台默认」。
 */
@AutoConfigureMockMvc
public abstract class TenantContractSupport extends IntegrationTestBase {

    @Autowired protected MockMvc mvc;
    @Autowired protected JwtUtils jwtUtils;
    @Autowired protected StringRedisTemplate redis;
    @Autowired protected ObjectMapper objectMapper;

    protected String token(String username, int tenantId) {
        String token = jwtUtils.generateToken(Map.of("tenantId", tenantId), username, 3600);
        redis.opsForValue().set(RedisKeyConstants.TOKEN_PREFIX + token, "1");
        return token;
    }

    protected MockHttpServletResponse perform(MockHttpServletRequestBuilder request, String token) throws Exception {
        request.header("Authorization", "Bearer " + token);
        return mvc.perform(request).andReturn().getResponse();
    }

    protected JsonNode call(MockHttpServletRequestBuilder request, String token) throws Exception {
        return objectMapper.readTree(perform(request, token).getContentAsString(StandardCharsets.UTF_8));
    }

    protected static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder b, String body) {
        return b.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    protected static JsonNode ok(JsonNode res) {
        assertEquals(0, res.path("code").asInt(), res.toString());
        return res.path("data");
    }

    protected static JsonNode fail(JsonNode res) {
        assertTrue(res.path("code").asInt() != 0, "应当失败：" + res);
        return res;
    }

    protected String write(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
