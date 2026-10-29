package com.zhul.erp.modules.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.common.constants.RedisKeyConstants;
import com.zhul.erp.common.utils.JwtUtils;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/** 菜单资源是全局配置：写操作要求菜单与按钮权限，且只有平台账号能改（openspec menu-write-guard） */
@AutoConfigureMockMvc
class MenuWriteGuardContractTest extends IntegrationTestBase {

    private static final String BASE = "/api/v1/system/menus";
    private static final int TENANT = 99011;
    private static final int PACKAGE = 99011;
    private static final int TENANT_ADMIN_ID = 99000041;
    private static final String TENANT_ADMIN = "it_menu_write_tenant";
    /** 菜单管理页面与它的新增、编辑、删除按钮 */
    private static final int MENU_PAGE = 100013;
    /** 用作被修改对象的菜单：AI 设置 */
    private static final int TARGET = 100118;

    @Autowired private MockMvc mvc;
    @Autowired private JwtUtils jwtUtils;
    @Autowired private StringRedisTemplate redis;
    @Autowired private ObjectMapper objectMapper;

    private Map<String, Object> before;

    @BeforeEach
    void setUp() {
        cleanup();
        before = jdbc.queryForMap("select name, sort, status, permission from resource where id = ?", TARGET);
        jdbc.update("insert into tenant_package (id, name, menu_ids) values (?, 'IT-菜单写保护', '[100013, 110021, 110022, 110023]')",
                PACKAGE);
        jdbc.update("insert into tenant (id, name, package_id) values (?, 'IT-菜单写保护', ?)", TENANT, PACKAGE);
        jdbc.update("insert into account (id, tenant_id, user_id, username, admin_flag) values (?, ?, ?, ?, 1)",
                TENANT_ADMIN_ID, TENANT, TENANT_ADMIN_ID, TENANT_ADMIN);
    }

    @AfterEach
    void tearDown() {
        jdbc.update("update resource set name = ?, sort = ?, status = ?, permission = ? where id = ?",
                before.get("name"), before.get("sort"), before.get("status"), before.get("permission"), TARGET);
        cleanup();
    }

    private void cleanup() {
        jdbc.update("delete from account where id = ?", TENANT_ADMIN_ID);
        jdbc.update("delete from tenant where id = ?", TENANT);
        jdbc.update("delete from tenant_package where id = ?", PACKAGE);
    }

    @Test
    void tenantAdminWithButtonsCannotWriteGlobalMenus() throws Exception {
        String token = token(TENANT_ADMIN, TENANT);
        assertEquals(403, call(put(BASE + "/" + TARGET + "/sort").contentType(MediaType.APPLICATION_JSON)
                .content("{\"sort\":999}"), token).path("code").asInt());
        assertEquals(403, call(put(BASE + "/" + TARGET + "/status").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":0}"), token).path("code").asInt());
        assertEquals(403, call(put(BASE + "/" + TARGET).contentType(MediaType.APPLICATION_JSON)
                .content("{\"pid\":100084,\"name\":\"改名\",\"type\":2,\"sort\":7,\"status\":1,\"permission\":\"x\"}"), token)
                .path("code").asInt());
        assertEquals(403, call(post(BASE).contentType(MediaType.APPLICATION_JSON)
                .content("{\"pid\":100084,\"name\":\"新菜单\",\"type\":2,\"sort\":9,\"status\":1}"), token).path("code").asInt());
        assertEquals(403, call(delete(BASE + "/" + TARGET), token).path("code").asInt());

        Map<String, Object> after = jdbc.queryForMap("select name, sort, status, permission from resource where id = ?", TARGET);
        assertEquals(before, after);
        assertEquals(0, jdbc.queryForObject("select count(*) from resource where name = '新菜单'", Integer.class));
    }

    @Test
    void platformAccountNeedsTheButtonPermission() throws Exception {
        loginWithResources("it_menu_viewer", MENU_PAGE);
        JsonNode denied = call(put(BASE + "/" + TARGET + "/sort").contentType(MediaType.APPLICATION_JSON)
                .content("{\"sort\":999}"), token("it_menu_viewer", 0));
        assertEquals(403, denied.path("code").asInt(), denied.toString());
        assertEquals(before.get("sort"), jdbc.queryForObject("select sort from resource where id = ?", Integer.class, TARGET));
    }

    @Test
    void platformAdminCanStillMaintainMenus() throws Exception {
        loginAsAdmin("it_menu_admin");
        JsonNode ok = call(put(BASE + "/" + TARGET + "/sort").contentType(MediaType.APPLICATION_JSON)
                .content("{\"sort\":999}"), token("it_menu_admin", 0));
        assertEquals(0, ok.path("code").asInt(), ok.toString());
        assertEquals(999, jdbc.queryForObject("select sort from resource where id = ?", Integer.class, TARGET));
    }

    private String token(String username, int tenant) {
        String token = jwtUtils.generateToken(Map.of("tenantId", tenant), username, 3600);
        redis.opsForValue().set(RedisKeyConstants.TOKEN_PREFIX + token, "1");
        return token;
    }

    private JsonNode call(MockHttpServletRequestBuilder request, String token) throws Exception {
        request.header("Authorization", "Bearer " + token);
        return objectMapper.readTree(mvc.perform(request).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}
