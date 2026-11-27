package com.zhul.erp.modules.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhul.erp.support.TenantContractSupport;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * audit SEC-018 / SEC-019：系统管理的读取接口按对应菜单校验；负责人下拉用的用户选项登录即可用，且不含手机号、邮箱
 */
class SystemReadPermissionContractTest extends TenantContractSupport {

    /** IntegrationTestBase.loginWithResources 建的测试用户 ID */
    private static final long ME = 99000002L;
    /** 别的租户的测试用户 */
    private static final long OTHER_TENANT_USER = 99000917L;
    private static final int MENU_USER = 100011;
    private static final int MENU_ROLE = 100012;
    private static final int MENU_MENU = 100013;
    private static final int MENU_CONFIG = 100017;
    private static final int MENU_OPERATE_LOG = 100018;
    private static final int MENU_SALES_ORDERS = 100079;

    private static final String LOGS = "/api/v1/system/logs/operate";
    private static final String CONFIGS = "/api/v1/system/configs";
    private static final String ROLES = "/api/v1/system/roles";
    private static final String MENUS = "/api/v1/system/menus";
    private static final String USERS = "/api/v1/system/users";

    private int status(String url, String token) throws Exception {
        return perform(get(url), token).getStatus();
    }

    private String as(String username, int... menus) {
        loginWithResources(username, menus);
        return token(username, 0);
    }

    @Test
    void salesUser_cannotReadSystemManagement() throws Exception {
        String sales = as("it_sys_sales", MENU_SALES_ORDERS);
        for (String url : List.of(LOGS, LOGS + "/modules", LOGS + "/1", LOGS + "/export", CONFIGS, CONFIGS + "/group-counts",
                ROLES, ROLES + "/all", ROLES + "/stats", ROLES + "/1/delete-check", ROLES + "/admin/depts",
                MENUS + "/1/delete-check", MENUS + "/role/admin", USERS, USERS + "/stats")) {
            assertEquals(403, status(url, sales), url);
        }
        assertEquals(200, status(MENUS, sales), "侧边栏按菜单树生成，所有登录用户都要读");
    }

    @Test
    void eachMenu_opensItsOwnReads() throws Exception {
        String logs = as("it_sys_log", MENU_OPERATE_LOG);
        assertEquals(200, status(LOGS, logs));
        assertEquals(200, status(LOGS + "/modules", logs));
        assertEquals(403, status(CONFIGS, logs));

        String config = as("it_sys_cfg", MENU_CONFIG);
        assertEquals(200, status(CONFIGS, config));
        assertEquals(200, status(CONFIGS + "/group-counts", config));

        String role = as("it_sys_role", MENU_ROLE);
        assertEquals(200, status(ROLES, role));
        assertEquals(200, status(ROLES + "/stats", role));
        assertEquals(200, status(ROLES + "/admin/depts", role));
        assertEquals(200, status(MENUS + "/role/admin", role));
        assertEquals(403, status(USERS, role));

        String user = as("it_sys_user", MENU_USER);
        assertEquals(200, status(USERS, user));
        assertEquals(200, status(USERS + "/stats", user));
        assertEquals(200, status(ROLES + "/all", user), "用户管理要用角色下拉");
        assertEquals(403, status(ROLES, user));

        assertEquals(200, status(MENUS + "/1/delete-check", as("it_sys_menu", MENU_MENU)));
    }

    @Test
    void userOptions_loginOnly_idAndNameOnly() throws Exception {
        String sales = as("it_sys_opt", MENU_SALES_ORDERS);
        jdbc.update("update user_basic set phone = '13800000000', email = 'it@example.com' where id = ?", ME);
        JsonNode options = ok(call(get(USERS + "/options").param("name", "IT").param("status", "1"), sales));
        assertTrue(options.size() > 0);
        String body = options.toString();
        assertFalse(body.contains("13800000000") || body.contains("it@example.com"), "不含手机号、邮箱");
        JsonNode first = options.get(0);
        assertTrue(first.has("id") && first.has("name"));
        assertFalse(first.has("phone") || first.has("email") || first.has("username"));
        assertEquals(1, ok(call(get(USERS + "/options").param("limit", "1"), sales)).size(), "按 limit 截断");
    }

    @Test
    void userNames_loginOnly_mapsUsernameToName() throws Exception {
        String sales = as("it_sys_names", MENU_SALES_ORDERS);
        jdbc.update("update user_basic set name = '测试员', email = 'it@example.com' where id = ?", ME);
        // 别的租户的账号（同一账号名可以出现在不同租户）不能查到
        jdbc.update("delete from user_basic where id = ?", OTHER_TENANT_USER);
        jdbc.update("insert into user_basic (id, tenant_id, name, username, status) values (?, 9917, '别家员工', 'it_sys_other', 1)",
                OTHER_TENANT_USER);
        try {
            JsonNode names = ok(call(get(USERS + "/names")
                    .param("usernames", "it_sys_names,sys,nobody,it_sys_other"), sales));
            assertEquals("测试员", names.path("it_sys_names").asText());
            assertEquals(1, names.size(), "查不到的账号、别的租户的账号都不返回");
            assertFalse(names.toString().contains("it@example.com"), "不含邮箱");
        } finally {
            jdbc.update("delete from user_basic where id = ?", OTHER_TENANT_USER);
        }
    }
}
