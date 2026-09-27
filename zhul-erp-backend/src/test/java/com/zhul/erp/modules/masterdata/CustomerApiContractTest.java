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
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * 外贸客户档案接口契约（enrich-customer-trade-profile）：走完整的 HTTP + JWT + 权限 + 数据权限链路，
 * 连测试库执行真实 SQL（迁移脚本、查重、引用判定、数据范围过滤）。
 * 测试账号固定为 IntegrationTestBase 的 99000002，没有配置角色，所以非管理员登录时数据范围为「仅本人」。
 */
@AutoConfigureMockMvc
class CustomerApiContractTest extends IntegrationTestBase {

    private static final String BASE = "/api/v1/masterdata/customers";
    private static final long SELF = 99000002L;
    private static final long OTHER_OWNER = 99000011L;
    private static final int RES_EDIT = 110142;
    private static final int RES_EXPORT = 110146;

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JwtUtils jwtUtils;
    @Autowired
    private StringRedisTemplate redis;
    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanData() {
        cleanup();
        jdbc.update("insert into user_basic (id, tenant_id, name, username, role_code, status) values (?, 0, '赵敏', 'it_zhaomin', '', 1)",
                OTHER_OWNER);
    }

    @AfterEach
    void cleanAfter() {
        cleanup();
    }

    private void cleanup() {
        jdbc.update("delete from customer_inquiry where tenant_id = 0 and customer_id in (select id from customer where tenant_id = 0)");
        jdbc.update("delete from customer_party where tenant_id = 0");
        jdbc.update("delete from customer where tenant_id = 0");
        jdbc.update("delete from user_basic where id = ?", OTHER_OWNER);
        jdbc.update("delete from sys_log where menu = '客户管理'");
    }

    private String token(String username) {
        String token = jwtUtils.generateToken(Map.of("tenantId", 0), username, 3600);
        redis.opsForValue().set(RedisKeyConstants.TOKEN_PREFIX + token, "1");
        return token;
    }

    private MockHttpServletResponse perform(MockHttpServletRequestBuilder request, String token) throws Exception {
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return mvc.perform(request).andReturn().getResponse();
    }

    private JsonNode call(MockHttpServletRequestBuilder request, String token) throws Exception {
        return objectMapper.readTree(perform(request, token).getContentAsString(StandardCharsets.UTF_8));
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String body) {
        return builder.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private long create(String token, String body) throws Exception {
        JsonNode res = call(json(post(BASE), body), token);
        assertEquals(0, res.path("code").asInt(), res.toString());
        return res.path("data").path("id").asLong();
    }

    private static final String FULL = """
            {"name":"ABC Automation GmbH","nameCn":"德国ABC自动化","customerRole":3,"customerGrade":1,"sourceChannel":1,
             "country":"germany","city":"München","address":"Industriestraße 12","taxId":"DE123456789",
             "timezone":"Europe/Berlin","contactName":"Thomas Müller","contactPhone":"+49 (89) 1234-5678",
             "currency":"EUR","incoterm":"CIF","incotermPlace":"Hamburg","paymentMethod":8,"paymentDays":60,
             "creditLimit":200000.00,"creditCurrency":"EUR",
             "parties":[
               {"partyType":1,"companyName":"ABC Automation GmbH","country":"Germany","address":"Industriestrasse 12"},
               {"partyType":1,"companyName":"ABC Logistics Center GmbH","country":"Germany","address":"Hafenstrasse 88","defaultParty":true},
               {"partyType":2,"companyName":"Kuehne Freight GmbH","country":"Germany","address":"Am Sandtorkai 5","destinationPort":"Hamburg"}
             ]}
            """;

    @Test
    void createFullProfile_detailHasPartiesWithDefaults_refIsSlim() throws Exception {
        loginAsAdmin("cust_admin");
        String admin = token("cust_admin");
        long id = create(admin, FULL);

        JsonNode detail = call(get(BASE + "/" + id + "/detail"), admin).path("data");
        assertEquals("Germany", detail.path("country").asText());
        assertTrue(detail.path("customerCode").asText().startsWith("CUS"));
        assertEquals(SELF, detail.path("ownerId").asLong());
        assertEquals(60, detail.path("paymentDays").asInt());
        assertEquals("200000.00", jdbc.queryForObject("select credit_limit from customer where id = ?", String.class, id));
        JsonNode parties = detail.path("parties");
        assertEquals(3, parties.size());
        // 收货人第二条被标记为默认，第一条不是；通知方唯一一条自动默认，且目的港被清空（仅收货人使用）
        assertFalse(parties.get(0).path("defaultParty").asBoolean());
        assertTrue(parties.get(1).path("defaultParty").asBoolean());
        assertTrue(parties.get(2).path("defaultParty").asBoolean());
        assertEquals("", parties.get(2).path("destinationPort").asText());

        JsonNode ref = call(get(BASE + "/" + id), admin).path("data");
        assertEquals("ABC Automation GmbH", ref.path("name").asText());
        assertTrue(ref.path("contactName").isMissingNode(), "引用接口不应返回联系人等字段");

        String nameKey = jdbc.queryForObject("select name_key from customer where id = ?", String.class, id);
        assertEquals("abcautomation", nameKey);
    }

    @Test
    void invalidInput_returns400() throws Exception {
        loginAsAdmin("cust_admin");
        String admin = token("cust_admin");
        for (String body : new String[]{
            "{\"name\":\"A\",\"country\":\"Germany\",\"contactPhone\":\"call me\"}",
            "{\"name\":\"A\",\"country\":\"Germany\",\"currency\":\"XXX\"}",
            "{\"name\":\"A\",\"country\":\"Germany\",\"website\":\"abc.com\"}",
            "{\"name\":\"A\",\"country\":\"Germany\",\"customerCode\":\"CUS-1\"}",
            "{\"name\":\"A\",\"country\":\"Germany\",\"depositRatio\":120}",
            "{\"name\":\"A\"}"
        }) {
            assertEquals(400, perform(json(post(BASE), body), admin).getStatus(), body);
        }
    }

    @Test
    void duplicateCheck_ignoresSuffixAndCase_butNotCountry() throws Exception {
        loginAsAdmin("cust_admin");
        String admin = token("cust_admin");
        create(admin, "{\"name\":\"ABC Automation GmbH\",\"country\":\"Germany\"}");

        JsonNode dup = call(json(post(BASE), "{\"name\":\"abc automation\",\"country\":\"Germany\"}"), admin);
        assertEquals("CUSTOMER_DUPLICATE", dup.path("data").path("errorCode").asText(), dup.toString());
        assertEquals("该客户已存在，负责业务员：IT", dup.path("message").asText());

        create(admin, "{\"name\":\"ABC Automation GmbH\",\"country\":\"Austria\"}");
    }

    @Test
    void selfScope_seesOnlyOwnCustomers() throws Exception {
        loginAsAdmin("cust_admin");
        String admin = token("cust_admin");
        long own = create(admin, "{\"name\":\"Own Customer Ltd\",\"country\":\"Germany\"}");
        long others = create(admin, "{\"name\":\"Others Customer Ltd\",\"country\":\"Germany\"}");
        jdbc.update("update customer set owner_id = ? where id = ?", OTHER_OWNER, others);

        loginWithResources("cust_staff", RES_EDIT);
        String staff = token("cust_staff");
        JsonNode page = call(get(BASE + "/page"), staff).path("data");
        assertEquals(1, page.path("total").asInt());
        assertEquals(own, page.path("records").get(0).path("id").asLong());

        assertEquals("客户不存在或无权查看", call(get(BASE + "/" + others + "/detail"), staff).path("message").asText());
        assertEquals(1, call(get(BASE), staff).path("data").size(), "客户选择器也按数据范围过滤");
        assertEquals("Others Customer Ltd", call(get(BASE + "/" + others), staff).path("data").path("name").asText(),
                "引用接口供询盘回显，不按数据范围过滤");

        JsonNode dup = call(json(post(BASE), "{\"name\":\"Others Customer\",\"country\":\"Germany\"}"), staff);
        assertEquals("该客户已存在，负责业务员：赵敏", dup.path("message").asText());
        assertFalse(dup.path("data").path("detail").path("selectable").asBoolean());

        assertEquals("SELF", call(get(BASE + "/assignable-owners"), staff).path("data").path("scope").asText());
    }

    @Test
    void permissions_editTransferExportRequireButtons() throws Exception {
        loginAsAdmin("cust_admin");
        long id = create(token("cust_admin"), "{\"name\":\"Perm Test Ltd\",\"country\":\"Germany\"}");

        loginWithResources("cust_viewer");
        String viewer = token("cust_viewer");
        assertEquals(403, perform(json(put(BASE + "/" + id),
                "{\"name\":\"Perm Test Ltd\",\"country\":\"Germany\",\"customerRole\":1,\"status\":1}"), viewer).getStatus());
        assertEquals(403, perform(json(post(BASE + "/transfer"), "{\"ids\":[" + id + "],\"ownerId\":" + OTHER_OWNER + "}"), viewer).getStatus());
        assertEquals(403, perform(get(BASE + "/export"), viewer).getStatus());

        loginWithResources("cust_exporter", RES_EXPORT);
        MockHttpServletResponse res = perform(get(BASE + "/export"), token("cust_exporter"));
        assertEquals(200, res.getStatus());
        assertTrue(res.getContentType().startsWith("application/vnd.openxmlformats"));
    }

    @Test
    void deleteReferencedCustomer_isRejected() throws Exception {
        loginAsAdmin("cust_admin");
        String admin = token("cust_admin");
        long referenced = create(admin, "{\"name\":\"Referenced Ltd\",\"country\":\"Germany\"}");
        long free = create(admin, "{\"name\":\"Free Ltd\",\"country\":\"Germany\"}");
        jdbc.update("insert into customer_inquiry (tenant_id, customer_id, inquiry_date) values (0, ?, curdate())", referenced);

        assertEquals("该客户已有询盘或单据记录，不能删除，可改为禁用",
                call(delete(BASE + "/" + referenced), admin).path("message").asText());
        JsonNode batch = call(json(post(BASE + "/batch-delete"), "{\"ids\":[" + referenced + "," + free + ",987654321]}"), admin)
                .path("data");
        assertEquals(1, batch.path("deleted").asInt());
        assertEquals(1, batch.path("referenced").asInt());
        assertEquals(1, batch.path("missing").asInt());
    }

    @Test
    void transfer_updatesOwnerAndWritesOperateLog() throws Exception {
        loginAsAdmin("cust_admin");
        String admin = token("cust_admin");
        long id = create(admin, "{\"name\":\"Transfer Ltd\",\"country\":\"Germany\"}");

        JsonNode res = call(json(post(BASE + "/transfer"),
                "{\"ids\":[" + id + "],\"ownerId\":" + OTHER_OWNER + ",\"reason\":\"岗位调整\"}"), admin);
        assertEquals(0, res.path("code").asInt(), res.toString());

        assertEquals(OTHER_OWNER, jdbc.queryForObject("select owner_id from customer where id = ?", Long.class, id));
        String content = jdbc.queryForObject("select content from sys_log where menu = '客户管理' and operation = '转移客户'",
                String.class);
        JsonNode log = objectMapper.readTree(content);
        assertEquals("IT", log.path("before").path("ownerName").asText());
        assertEquals("赵敏", log.path("after").path("ownerName").asText());
        assertEquals("岗位调整", log.path("after").path("reason").asText());
    }

    @Test
    void countryTimezones_availableToLoggedInUser() throws Exception {
        loginAsAdmin("cust_admin");
        JsonNode data = call(get("/api/v1/masterdata/country-timezones"), token("cust_admin")).path("data");
        assertEquals("Europe/Berlin", data.path("DE").get(0).asText());
    }

    @Test
    void countryTimezones_rejectUnauthenticated() throws Exception {
        assertEquals(401, call(get("/api/v1/masterdata/country-timezones"), null).path("code").asInt());
    }
}
