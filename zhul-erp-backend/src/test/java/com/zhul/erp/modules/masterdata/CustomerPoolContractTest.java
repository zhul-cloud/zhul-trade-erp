package com.zhul.erp.modules.masterdata;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * 公海客户（spec master-data/customer-pool，openspec feedback-1116-batch2）：放入公海的权限、公海列表不受数据范围限制、
 * 认领与并发认领、公海客户只读、全部客户与建档统计不含公海客户。
 * 测试账号 99000002（姓名 IT）；另一个业务员 99000031（张伟）。非管理员没有配置数据权限，范围为「仅本人」。
 */
@AutoConfigureMockMvc
class CustomerPoolContractTest extends IntegrationTestBase {

    private static final String BASE = "/api/v1/masterdata/customers";
    private static final long SELF = 99000002L;
    private static final long OTHER = 99000031L;
    private static final int MENU_CUSTOMER = 100061;
    private static final int RES_EDIT = 110142;
    private static final int RES_TRANSFER = 110145;

    @Autowired private MockMvc mvc;
    @Autowired private JwtUtils jwtUtils;
    @Autowired private StringRedisTemplate redis;
    @Autowired private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        cleanup();
        jdbc.update("insert into user_basic (id, tenant_id, name, username, role_code, status) values (?, 0, '张伟', 'it_pool_zw', '', 1)",
                OTHER);
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    private void cleanup() {
        jdbc.update("delete from followup_item where tenant_id = 0");
        jdbc.update("delete from customer_contact where tenant_id = 0");
        jdbc.update("delete from customer_field_source where tenant_id = 0");
        jdbc.update("delete from customer where tenant_id = 0");
        jdbc.update("delete from user_basic where id = ?", OTHER);
    }

    private String token(String username) {
        String token = jwtUtils.generateToken(Map.of("tenantId", 0), username, 3600);
        redis.opsForValue().set(RedisKeyConstants.TOKEN_PREFIX + token, "1");
        return token;
    }

    private JsonNode call(MockHttpServletRequestBuilder r, String token) throws Exception {
        r.header("Authorization", "Bearer " + token);
        return objectMapper.readTree(mvc.perform(r).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private JsonNode ok(JsonNode res) {
        assertEquals(0, res.path("code").asInt(), res.toString());
        return res.path("data");
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder b, Object body) throws Exception {
        return b.contentType(MediaType.APPLICATION_JSON).content(new ObjectMapper().writeValueAsString(body));
    }

    /** 直接建客户（不经接口，避免触发其他保存逻辑），负责人为 owner */
    private long customer(String name, long owner) {
        jdbc.update("insert into customer (tenant_id, customer_code, name, name_key, name_cn, country, owner_id, status) "
                + "values (0, ?, ?, ?, '', 'Germany', ?, 1)", "ITP" + System.nanoTime() % 1000000, name,
                name.toLowerCase().replace(" ", ""), owner);
        return jdbc.queryForObject("select max(id) from customer where tenant_id = 0", Long.class);
    }

    private static List<Long> ids(JsonNode records) {
        List<Long> out = new ArrayList<>();
        records.forEach(r -> out.add(r.path("id").asLong()));
        return out;
    }

    @Test
    void salesman_putsOwnCustomer_poolVisibleToAll_readOnly_claim() throws Exception {
        loginWithResources("it_pool_li", MENU_CUSTOMER, RES_EDIT);
        String li = token("it_pool_li");
        long mine = customer("Pool Mine GmbH", SELF);
        long others = customer("Pool Others GmbH", OTHER);

        // 不是负责人也没有转移权限：不能放入
        JsonNode denied = call(json(post(BASE + "/pool"), Map.of("ids", List.of(others), "reason", "长期无回复")), li);
        assertTrue(denied.path("message").asText().contains("只有负责人或有客户转移权限的人"), denied.toString());
        // 原因必填
        assertEquals(400, mvc.perform(json(post(BASE + "/pool"), Map.of("ids", List.of(mine), "reason", " "))
                .header("Authorization", "Bearer " + li)).andReturn().getResponse().getStatus());

        ok(call(json(post(BASE + "/pool"), Map.of("ids", List.of(mine), "reason", "长期无回复：半年没回")), li));
        Map<String, Object> row = jdbc.queryForMap("select owner_id, in_pool, last_owner_id, pool_by, pool_reason from customer where id = ?", mine);
        assertEquals(0L, ((Number) row.get("owner_id")).longValue());
        assertEquals(1, ((Number) row.get("in_pool")).intValue());
        assertEquals(SELF, ((Number) row.get("last_owner_id")).longValue());
        assertEquals(SELF, ((Number) row.get("pool_by")).longValue());
        JsonNode again = call(json(post(BASE + "/pool"), Map.of("ids", List.of(mine), "reason", "x")), li);
        assertTrue(again.path("message").asText().contains("已在公海"), again.toString());

        // 「仅本人」的业务员也能看公海列表与公海客户详情
        jdbc.update("update customer set in_pool = 1, owner_id = 0, last_owner_id = ?, pool_reason = '不是目标客户' where id = ?", OTHER, others);
        JsonNode pool = ok(call(get(BASE + "/pool"), li));
        assertEquals(List.of(mine, others).stream().sorted().toList(), ids(pool.path("records")).stream().sorted().toList());
        JsonNode first = pool.path("records").get(0);
        assertTrue(first.path("inPool").asBoolean());
        JsonNode detail = ok(call(get(BASE + "/" + others + "/detail"), li));
        assertTrue(detail.path("inPool").asBoolean());
        assertEquals("张伟", detail.path("lastOwnerName").asText());
        // 认领前只读
        JsonNode edit = call(json(put(BASE + "/" + mine), Map.of("name", "Pool Mine GmbH", "country", "Germany",
                "customerRole", 2, "currency", "USD", "status", 1)), li);
        assertTrue(edit.path("message").asText().contains("在公海"), edit.toString());

        // 认领：成为负责人，离开公海
        ok(call(post(BASE + "/" + others + "/claim"), li));
        Map<String, Object> claimed = jdbc.queryForMap("select owner_id, in_pool from customer where id = ?", others);
        assertEquals(SELF, ((Number) claimed.get("owner_id")).longValue());
        assertEquals(0, ((Number) claimed.get("in_pool")).intValue());
        assertEquals(List.of(mine), ids(ok(call(get(BASE + "/pool"), li)).path("records")));

        // 别人先认领了：提示已被谁认领
        jdbc.update("update customer set in_pool = 0, owner_id = ? where id = ?", OTHER, mine);
        JsonNode late = call(post(BASE + "/" + mine + "/claim"), li);
        assertEquals("已被 张伟 认领", late.path("message").asText(), late.toString());
    }

    @Test
    void transferPermission_putsOthersCustomer_allCustomersExcludePool_cannotTransferPool() throws Exception {
        loginAsAdmin("it_pool_admin");
        String admin = token("it_pool_admin");
        long a = customer("Pool Alpha GmbH", OTHER);
        long b = customer("Pool Beta GmbH", OTHER);

        ok(call(json(post(BASE + "/pool"), Map.of("ids", List.of(a), "reason", "业务员离职")), admin));
        assertEquals(OTHER, jdbc.queryForObject("select last_owner_id from customer where id = ?", Long.class, a));

        List<Long> all = ids(ok(call(get(BASE + "/page").param("pageSize", "50"), admin)).path("records"));
        assertTrue(all.contains(b));
        assertFalse(all.contains(a), "全部客户不含公海客户");
        assertEquals(1, ok(call(get(BASE + "/profile-counts"), admin)).path("all").asLong(), "建档统计不含公海客户");

        JsonNode transfer = call(json(post(BASE + "/transfer"), Map.of("ids", List.of(a), "ownerId", OTHER)), admin);
        assertTrue(transfer.path("message").asText().contains("要先认领"), transfer.toString());
    }
}
