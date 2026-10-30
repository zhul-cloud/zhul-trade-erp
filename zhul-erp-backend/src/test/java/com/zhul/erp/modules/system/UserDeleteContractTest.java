package com.zhul.erp.modules.system;

import cn.hutool.crypto.digest.BCrypt;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.common.result.ResultCode;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.system.dto.UserPageQuery;
import com.zhul.erp.modules.system.service.UserService;
import com.zhul.erp.modules.tenant.service.TenantService;
import com.zhul.erp.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** 删除用户是软删除：列表和统计不再显示、不能登录，启用停用租户也不会把人恢复；停用要同步到登录账号 */
@AutoConfigureMockMvc
class UserDeleteContractTest extends IntegrationTestBase {

    private static final int TENANT_ID = 99000061;
    private static final int USER_ID = 99000061;
    private static final String USERNAME = "it_user_delete";
    private static final String NAME = "删除测试员";
    private static final String PASSWORD = "Pass@123456";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserService userService;
    @Autowired private TenantService tenantService;

    @BeforeEach
    void user() {
        cleanup();
        jdbc.update("insert into tenant (id, name, status) values (?, '删除测试租户', 1)", TENANT_ID);
        jdbc.update("insert into user_basic (id, tenant_id, name, username, status) values (?, ?, ?, ?, 1)",
                USER_ID, TENANT_ID, NAME, USERNAME);
        jdbc.update("insert into account (id, tenant_id, user_id, username, admin_flag, status) values (?, ?, ?, ?, 0, 1)",
                USER_ID, TENANT_ID, USER_ID, USERNAME);
        jdbc.update("insert into account_local_auth (account_id, username, password, salt) values (?, ?, ?, '')",
                USER_ID, USERNAME, BCrypt.hashpw(PASSWORD, BCrypt.gensalt()));
        TenantContext.setTenantId(TENANT_ID);
    }

    @AfterEach
    void cleanup() {
        jdbc.update("delete from sys_log where tenant_id = ?", TENANT_ID);
        jdbc.update("delete from account_access_token where account_id = ?", USER_ID);
        jdbc.update("delete from account_local_auth where account_id = ?", USER_ID);
        jdbc.update("delete from account where id = ?", USER_ID);
        jdbc.update("delete from user_basic where id = ?", USER_ID);
        jdbc.update("delete from tenant where id = ?", TENANT_ID);
    }

    private long listed() {
        UserPageQuery query = new UserPageQuery();
        query.setName(NAME);
        return userService.listUsers(query).getTotal();
    }

    private int loginCode() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("username", USERNAME, "password", PASSWORD,
                "idempotencyKey", UUID.randomUUID().toString()));
        JsonNode res = objectMapper.readTree(mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        return res.path("code").asInt();
    }

    private int status(String table, String idColumn) {
        return jdbc.queryForObject("select status from " + table + " where " + idColumn + " = ?", Integer.class, USER_ID);
    }

    @Test
    void deletedUserDisappearsFromListAndStatsAndCannotLogin() throws Exception {
        assertEquals(1, listed());
        assertEquals(1, userService.getUserStats().getTotal());

        userService.deleteUser(USER_ID);

        assertEquals(0, listed());
        assertEquals(0, userService.getUserStats().getTotal());
        assertEquals(0, userService.getUserStats().getDisabled());
        assertEquals(UserService.STATUS_DELETED, status("user_basic", "id"));
        assertEquals(UserService.STATUS_DELETED, status("account", "user_id"));
        assertEquals(ResultCode.ACCOUNT_DISABLED.getCode(), loginCode());
    }

    @Test
    void disablingUserBlocksLoginAndEnablingRestoresIt() throws Exception {
        userService.updateUserStatus(USER_ID, 0);
        assertEquals(0, status("account", "user_id"));
        assertEquals(ResultCode.ACCOUNT_DISABLED.getCode(), loginCode());

        userService.updateUserStatus(USER_ID, 1);
        assertEquals(1, status("account", "user_id"));
        assertEquals(0, loginCode());
    }

    @Test
    void deletedUserStaysDeletedWhenStatusOrTenantChanges() {
        userService.deleteUser(USER_ID);

        userService.updateUserStatus(USER_ID, 1);
        TenantContext.setTenantId(0);
        tenantService.updateStatus(TENANT_ID, 0);
        tenantService.updateStatus(TENANT_ID, 1);

        assertEquals(UserService.STATUS_DELETED, status("user_basic", "id"));
        assertEquals(UserService.STATUS_DELETED, status("account", "user_id"));
    }
}
