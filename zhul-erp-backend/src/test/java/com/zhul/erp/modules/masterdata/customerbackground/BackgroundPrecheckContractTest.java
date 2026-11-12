package com.zhul.erp.modules.masterdata.customerbackground;

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
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** spec crm/customer-background「背调前检查」：检查项、充足度、补充位置、数据范围 */
@AutoConfigureMockMvc
class BackgroundPrecheckContractTest extends IntegrationTestBase {

    private static final String BASE = "/api/v1/masterdata/customers";

    @Autowired private MockMvc mvc;
    @Autowired private JwtUtils jwtUtils;
    @Autowired private StringRedisTemplate redis;
    @Autowired private ObjectMapper objectMapper;

    private String admin;

    @BeforeEach
    void setUp() {
        cleanup();
        loginAsAdmin("it_pc_admin");
        admin = token("it_pc_admin");
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    private void cleanup() {
        jdbc.update("delete from customer_inquiry where tenant_id = 0 and remark = 'it_pc'");
        jdbc.update("delete from customer_contact where tenant_id = 0");
        jdbc.update("delete from customer where tenant_id = 0");
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

    private long customer(String extra) throws Exception {
        String body = "{\"name\":\"Pacific Controls Pty Ltd\",\"country\":\"Australia\",\"customerRole\":2,\"currency\":\"USD\","
                + "\"contactName\":\"James Walker\"" + extra + "}";
        return ok(call(post(BASE).contentType(MediaType.APPLICATION_JSON).content(body), admin)).path("id").asLong();
    }

    private Map<String, JsonNode> items(JsonNode data) {
        Map<String, JsonNode> m = new HashMap<>();
        data.path("items").forEach(i -> m.put(i.path("key").asText(), i));
        return m;
    }

    private JsonNode precheck(long id, String token) throws Exception {
        return call(get(BASE + "/" + id + "/background-reports/precheck"), token);
    }

    private void inquiry(long customerId, String code, String content) {
        LocalDate d = LocalDate.of(2026, 11, 1);
        jdbc.update("insert into customer_inquiry (tenant_id, inquiry_code, customer_id, raw_content, inquiry_date, quote_deadline, remark) "
                + "values (0, ?, ?, ?, ?, ?, 'it_pc')", code, customerId, content, d, d);
    }

    @Test
    void onlyNameAndCountry_low_withReasonsAndFixTargets() throws Exception {
        long id = customer("");
        jdbc.update("update customer_contact set email = 'james@gmail.com' where customer_id = ?", id);
        JsonNode d = ok(precheck(id, admin));
        assertEquals("LOW", d.path("level").asText());
        assertEquals(0, d.path("keyCount").asInt());
        Map<String, JsonNode> m = items(d);
        assertEquals("MISSING", m.get("website").path("state").asText());
        assertEquals("profile:1", m.get("website").path("fixTarget").asText());
        assertEquals("WARN", m.get("companyEmail").path("state").asText());
        assertEquals("联系人用的是 gmail.com，找不到公司", m.get("companyEmail").path("reason").asText());
        assertEquals("contact", m.get("companyEmail").path("fixTarget").asText());
        assertEquals("MISSING", m.get("inquiryText").path("state").asText());
        assertTrue(m.get("inquiryText").path("fixTarget").isMissingNode());
        assertEquals("OK", m.get("basic").path("state").asText());
        assertEquals("Pacific Controls Pty Ltd · Australia", m.get("basic").path("value").asText());
        assertEquals("缺 行业、主营产品", m.get("companyInfo").path("reason").asText());
        assertEquals("profile:1", m.get("companyInfo").path("fixTarget").asText());
        assertEquals(6, d.path("items").size());
    }

    @Test
    void websiteOnly_medium_onlyProductsMissingPointsToTab2() throws Exception {
        long id = customer(",\"website\":\"https://pacificcontrols.com.au\"");
        jdbc.update("update customer set industry = 8 where id = ?", id);
        JsonNode d = ok(precheck(id, admin));
        assertEquals("MEDIUM", d.path("level").asText());
        Map<String, JsonNode> m = items(d);
        assertEquals("OK", m.get("website").path("state").asText());
        assertEquals("没有联系人邮箱", m.get("companyEmail").path("reason").asText());
        assertEquals("缺 主营产品", m.get("companyInfo").path("reason").asText());
        assertEquals("profile:2", m.get("companyInfo").path("fixTarget").asText());
    }

    @Test
    void companyEmailAndInquiries_high_inquiryCountCappedAtThree() throws Exception {
        long id = customer("");
        jdbc.update("update customer set industry = 8, main_products = '水厂与泵站自动化改造', "
                + "linkedin = 'https://www.linkedin.com/company/pacific-controls' where id = ?", id);
        jdbc.update("update customer_contact set email = 'james@pacificcontrols.com.au' where customer_id = ?", id);
        for (int k = 1; k <= 4; k++) {
            inquiry(id, "IT-PC-" + k, "Need S7-1200 x " + k);
        }
        inquiry(id, "IT-PC-EMPTY", "");
        JsonNode d = ok(precheck(id, admin));
        assertEquals("HIGH", d.path("level").asText());
        assertEquals(2, d.path("keyCount").asInt());
        Map<String, JsonNode> m = items(d);
        assertEquals("pacificcontrols.com.au（James Walker）", m.get("companyEmail").path("value").asText());
        assertEquals("3 张", m.get("inquiryText").path("value").asText());
        assertEquals("OK", m.get("linkedin").path("state").asText());
        assertEquals("系统集成商 · 水处理 · 水厂与泵站自动化改造", m.get("companyInfo").path("value").asText());
    }

    @Test
    void unknownCustomer_refused() throws Exception {
        JsonNode res = precheck(987654321L, admin);
        assertNotEquals(0, res.path("code").asInt(), res.toString());
    }
}
