package com.zhul.erp.modules.system;

import cn.hutool.crypto.digest.BCrypt;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.common.constants.RedisKeyConstants;
import com.zhul.erp.common.utils.JwtUtils;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.system.service.UserService;
import com.zhul.erp.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * 登录会话安全（audit 批次 A）：登录日志不暴露 Token（SEC-001）；停用、删除、重置密码撤销旧会话（SEC-004）；
 * 重置密码不走 URL（SEC-008）；邮箱找回密码同样撤销旧会话；通用 AI 任务创建接口已关闭（AI-003）
 */
@AutoConfigureMockMvc
class SessionSecurityContractTest extends IntegrationTestBase {

    private static final int USER_ID = 99000071;
    private static final String USERNAME = "it_session_target";
    private static final String PASSWORD = "Pass@123456";
    private static final int OTHER_TENANT = 99000072;
    private static final int OTHER_ID = 99000072;
    private static final int MENU_LOGIN_LOG = 100019;
    private static final int RES_FORCE_LOGOUT = 110081;
    private static final int RES_RESET_PWD = 110004;

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtUtils jwtUtils;
    @Autowired private StringRedisTemplate redis;
    @Autowired private UserService userService;

    @BeforeEach
    void user() {
        cleanup();
        jdbc.update("insert into user_basic (id, tenant_id, name, username, status) values (?, 0, '会话测试员', ?, 1)",
                USER_ID, USERNAME);
        jdbc.update("insert into account (id, tenant_id, user_id, username, admin_flag, status) values (?, 0, ?, ?, 0, 1)",
                USER_ID, USER_ID, USERNAME);
        jdbc.update("insert into account_local_auth (account_id, username, password, salt) values (?, ?, ?, '')",
                USER_ID, USERNAME, BCrypt.hashpw(PASSWORD, BCrypt.gensalt()));
        TenantContext.setTenantId(0);
    }

    @AfterEach
    void cleanup() {
        for (int id : new int[]{USER_ID, OTHER_ID}) {
            jdbc.queryForList("select access_token, refresh_token from account_access_token where account_id = ?", id)
                    .forEach(r -> {
                        redis.delete(RedisKeyConstants.TOKEN_PREFIX + r.get("access_token"));
                        if (r.get("refresh_token") != null) {
                            redis.delete(RedisKeyConstants.REFRESH_TOKEN_PREFIX + r.get("refresh_token"));
                        }
                    });
            jdbc.update("delete from account_access_token where account_id = ?", id);
            jdbc.update("delete from sys_log where operator_code = ?", String.valueOf(id));
            jdbc.update("delete from account_local_auth where account_id = ?", id);
            jdbc.update("delete from account where id = ?", id);
            jdbc.update("delete from user_basic where id = ?", id);
        }
    }

    /** 被测用户真实登录，返回 AccessToken */
    private String login(String password) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("username", USERNAME, "password", password,
                "idempotencyKey", UUID.randomUUID().toString()));
        JsonNode res = objectMapper.readTree(mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals(0, res.path("code").asInt(), res.toString());
        return res.path("data").path("accessToken").asText();
    }

    /** 管理员侧调用方：权限按 loginWithResources 设定的角色资源判定 */
    private String token(String username) {
        String token = jwtUtils.generateToken(Map.of("tenantId", 0), username, 3600);
        redis.opsForValue().set(RedisKeyConstants.TOKEN_PREFIX + token, "1");
        return token;
    }

    private MockHttpServletResponse perform(MockHttpServletRequestBuilder request, String token) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + token)).andReturn().getResponse();
    }

    private JsonNode call(MockHttpServletRequestBuilder request, String token) throws Exception {
        return objectMapper.readTree(perform(request, token).getContentAsString(StandardCharsets.UTF_8));
    }

    private boolean alive(String accessToken) {
        return Boolean.TRUE.equals(redis.hasKey(RedisKeyConstants.TOKEN_PREFIX + accessToken));
    }

    private Integer sessionId() {
        return jdbc.queryForObject("select id from account_access_token where account_id = ? and deleted_at is null",
                Integer.class, USER_ID);
    }

    private JsonNode targetRow(JsonNode page) {
        for (JsonNode r : page.path("data").path("records")) {
            if (USERNAME.equals(r.path("operatorName").asText()) && r.path("online").asBoolean()) {
                return r;
            }
        }
        return null;
    }

    @Test
    void loginLogKeepsSessionIdNotTokenAndNeedsMenu() throws Exception {
        String accessToken = login(PASSWORD);
        Integer sessionId = sessionId();
        String content = jdbc.queryForObject("select content from sys_log where type = 1 and operator_code = ?",
                String.class, String.valueOf(USER_ID));
        assertFalse(content.contains(accessToken), "登录日志不存 Token");
        assertEquals(sessionId.intValue(), objectMapper.readTree(content).path("session_id").asInt());

        loginWithResources("it_session_nomenu");
        String noMenu = token("it_session_nomenu");
        assertEquals(403, perform(get("/api/v1/system/logs/login"), noMenu).getStatus(), "普通登录用户不能查看登录日志");
        assertEquals(403, perform(get("/api/v1/system/logs/login/export"), noMenu).getStatus());

        loginWithResources("it_session_viewer", MENU_LOGIN_LOG);
        MockHttpServletResponse res = perform(get("/api/v1/system/logs/login").param("operatorName", USERNAME),
                token("it_session_viewer"));
        assertEquals(200, res.getStatus());
        String json = res.getContentAsString(StandardCharsets.UTF_8);
        assertFalse(json.contains(accessToken), "响应不含 Token");
        JsonNode row = targetRow(objectMapper.readTree(json));
        assertNotNull(row, "在线会话可见");
        assertEquals(sessionId.intValue(), row.path("sessionId").asInt());
        assertTrue(row.path("tokenId").isMissingNode());
    }

    @Test
    void forceLogoutBySessionIdOnlyWithinTenant() throws Exception {
        String accessToken = login(PASSWORD);
        // 另一租户的在线会话：会话 ID 可猜，不能被本租户下线
        jdbc.update("insert into account (id, tenant_id, user_id, username, admin_flag, status) values (?, ?, ?, 'it_session_other', 0, 1)",
                OTHER_ID, OTHER_TENANT, OTHER_ID);
        String otherToken = "it-other-" + UUID.randomUUID();
        jdbc.update("insert into account_access_token (user_id, account_id, access_token, status, expiry_time) "
                + "values (?, ?, ?, 1, date_add(now(), interval 1 hour))", OTHER_ID, OTHER_ID, otherToken);
        redis.opsForValue().set(RedisKeyConstants.TOKEN_PREFIX + otherToken, String.valueOf(OTHER_ID));
        Integer otherSession = jdbc.queryForObject("select id from account_access_token where account_id = ?",
                Integer.class, OTHER_ID);

        loginWithResources("it_session_admin", MENU_LOGIN_LOG, RES_FORCE_LOGOUT);
        String admin = token("it_session_admin");
        JsonNode foreign = call(post("/api/v1/system/logs/login/force-logout").contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionIds\":[" + otherSession + "]}"), admin);
        assertEquals(0, foreign.path("data").path("successCount").asInt(), foreign.toString());
        assertTrue(alive(otherToken), "其他租户的会话不受影响");

        JsonNode own = call(post("/api/v1/system/logs/login/force-logout").contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionIds\":[" + sessionId() + "]}"), admin);
        assertEquals(1, own.path("data").path("successCount").asInt(), own.toString());
        assertFalse(alive(accessToken));
    }

    @Test
    void disableDeleteAndResetPasswordRevokeSessions() throws Exception {
        String first = login(PASSWORD);
        userService.updateUserStatus(USER_ID, 0);
        assertFalse(alive(first), "停用后旧会话失效");
        assertEquals(0, jdbc.queryForObject("select count(*) from account_access_token where account_id = ? and deleted_at is null",
                Integer.class, USER_ID));

        userService.updateUserStatus(USER_ID, 1);
        String second = login(PASSWORD);
        loginWithResources("it_session_pwd", RES_RESET_PWD);
        String admin = token("it_session_pwd");
        String newPassword = "New@654321";
        JsonNode reset = call(put("/api/v1/system/users/" + USER_ID + "/password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"newPassword\":\"" + newPassword + "\"}"), admin);
        assertEquals(0, reset.path("code").asInt(), reset.toString());
        assertFalse(alive(second), "管理员重置密码后旧会话失效");
        assertTrue(BCrypt.checkpw(newPassword, jdbc.queryForObject(
                "select password from account_local_auth where account_id = ?", String.class, USER_ID)));
        assertTrue(perform(put("/api/v1/system/users/" + USER_ID + "/password").param("newPassword", "Url@111111"),
                admin).getStatus() >= 400, "新密码不再接受放在 URL 查询串里");

        String third = login(newPassword);
        userService.deleteUser(USER_ID);
        assertFalse(alive(third), "删除后旧会话失效");
    }

    @Test
    void emailPasswordResetRevokesSessions() throws Exception {
        String accessToken = login(PASSWORD);
        String verifyToken = "it-reset-" + UUID.randomUUID();
        redis.opsForValue().set(RedisKeyConstants.RESET_VERIFY_TOKEN_PREFIX + verifyToken, String.valueOf(USER_ID));
        String body = objectMapper.writeValueAsString(Map.of("verifyToken", verifyToken,
                "newPassword", "Mail@654321", "confirmPassword", "Mail@654321"));
        JsonNode res = objectMapper.readTree(mvc.perform(post("/api/v1/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals(0, res.path("code").asInt(), res.toString());
        assertFalse(alive(accessToken), "邮箱找回密码后旧会话失效");
    }

    @Test
    void genericAiTaskCreateEndpointClosed() throws Exception {
        loginAsAdmin("it_session_ai");
        int before = jdbc.queryForObject("select count(*) from ai_task", Integer.class);
        MockHttpServletResponse res = perform(post("/api/v1/ai-tasks").contentType(MediaType.APPLICATION_JSON)
                .content("{\"skillId\":\"inquiry-parse-and-split\",\"input\":{\"attachmentPaths\":[\"/etc/hosts\"]}}"),
                token("it_session_ai"));
        assertTrue(res.getStatus() >= 400, "通用创建接口不再开放：" + res.getStatus());
        assertEquals(before, jdbc.queryForObject("select count(*) from ai_task", Integer.class));
    }
}
