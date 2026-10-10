package com.zhul.erp.modules.inquiry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.common.constants.RedisKeyConstants;
import com.zhul.erp.common.utils.JwtUtils;
import com.zhul.erp.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

/**
 * 询价协作契约测试的公共部分：走完整的 HTTP + JWT + 权限 + 数据权限链路，连测试库执行真实 SQL。
 * 测试数据都在平台租户（tenant 0）；采购人员用角色 IT_BUYER（带「我的询价任务」「历史询价」菜单）与内置角色兼职采购。
 */
@AutoConfigureMockMvc
public abstract class InquiryContractSupport extends IntegrationTestBase {

    protected static final String INQ = "/api/v1/inquiry/customer-inquiries";
    protected static final String BOARD = "/api/v1/inquiry/sourcing-board";
    protected static final String MY = "/api/v1/inquiry/my-tasks";
    protected static final String HISTORY = "/api/v1/inquiry/price-history";
    protected static final long BUYER_LIN = 99000031L;
    protected static final long BUYER_JIANG = 99000032L;
    protected static final long PART_TIMER = 99000033L;
    protected static final String BUYER_ROLE = "IT_BUYER";
    protected static final int MENU_INQUIRY = 100051;
    protected static final int MENU_BOARD = 100054;
    protected static final int MENU_MY_TASKS = 100055;
    protected static final int MENU_HISTORY = 100056;
    protected static final int BTN_ASSIGN = 110171;
    protected static final int BTN_RULE = 110172;
    protected static final int BTN_PROXY = 110173;

    @Autowired protected MockMvc mvc;
    @Autowired protected JwtUtils jwtUtils;
    @Autowired protected StringRedisTemplate redis;
    @Autowired protected ObjectMapper objectMapper;

    @BeforeEach
    void setUpInquiryData() {
        cleanupInquiryData();
        for (int id : new int[] {MENU_INQUIRY, MENU_MY_TASKS, MENU_HISTORY}) {
            jdbc.update("insert into role_resource (role_code, resource_id, resource_code) values (?, ?, '')", BUYER_ROLE, id);
        }
        user(BUYER_LIN, "林熙", "it_lin", BUYER_ROLE);
        user(BUYER_JIANG, "江晓晞", "it_jiang", BUYER_ROLE);
        user(PART_TIMER, "王某", "it_wang", "ROLE_PTBUYER");
    }

    @AfterEach
    void tearDownInquiryData() {
        cleanupInquiryData();
    }

    private void user(long id, String name, String username, String role) {
        jdbc.update("insert into user_basic (id, tenant_id, name, username, role_code, status) values (?, 0, ?, ?, ?, 1)", id, name, username, role);
        jdbc.update("insert into account (id, tenant_id, user_id, username, admin_flag) values (?, 0, ?, ?, 0)", id, id, username);
    }

    protected void cleanupInquiryData() {
        for (String t : new String[] {"sourcing_import", "sourcing_quote", "sourcing_task_assignee", "sourcing_task", "sourcing_assign_rule",
                "inquiry_item", "customer_inquiry_attachment", "customer_inquiry", "customer"}) {
            jdbc.update("delete from " + t + " where tenant_id = 0");
        }
        jdbc.update("update sys_config set config_value = 'false' where tenant_id = 0 and config_key = 'inquiry.sourcing.auto-assign'");
        jdbc.update("delete from role_resource where role_code = ?", BUYER_ROLE);
        jdbc.update("delete from account where id in (?, ?, ?)", BUYER_LIN, BUYER_JIANG, PART_TIMER);
        jdbc.update("delete from user_basic where id in (?, ?, ?)", BUYER_LIN, BUYER_JIANG, PART_TIMER);
    }

    protected long customer(String name, String country) {
        jdbc.update("insert into customer (tenant_id, customer_code, name, country, contact_name, status) values (0, ?, ?, ?, 'Tom', 1)",
                "IT" + System.nanoTime() % 1_000_000_000L, name, country);
        return jdbc.queryForObject("select max(id) from customer where tenant_id = 0", Long.class);
    }

    protected String token(String username) {
        String token = jwtUtils.generateToken(Map.of("tenantId", 0), username, 3600);
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

    protected JsonNode ok(JsonNode res) {
        assertEquals(0, res.path("code").asInt(), res.toString());
        return res.path("data");
    }

    protected String write(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
