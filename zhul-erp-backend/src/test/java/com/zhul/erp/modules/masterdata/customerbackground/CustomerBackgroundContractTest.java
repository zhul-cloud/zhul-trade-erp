package com.zhul.erp.modules.masterdata.customerbackground;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.common.constants.RedisKeyConstants;
import com.zhul.erp.common.utils.JwtUtils;
import com.zhul.erp.modules.aitask.constants.AiConfigConstants;
import com.zhul.erp.modules.aitask.service.AiOrchestratorClient;
import com.zhul.erp.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** spec crm/customer-background「一键 AI 背调」：输入组装、同时只能一份背调中、24 小时提醒、权限、AI 未配置 */
@AutoConfigureMockMvc
class CustomerBackgroundContractTest extends IntegrationTestBase {

    private static final String BASE = "/api/v1/masterdata/customers";
    private static final int MENU_CUSTOMER = 100061;
    private static final int BTN_BACKGROUND = 110225;

    @Autowired private MockMvc mvc;
    @Autowired private JwtUtils jwtUtils;
    @Autowired private StringRedisTemplate redis;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private AiOrchestratorClient orchestrator;

    private String admin;

    @BeforeEach
    void setUp() {
        cleanup();
        seedAiConfig(0);
        loginAsAdmin("it_bg_admin");
        admin = token("it_bg_admin");
        when(orchestrator.submit(any(), anyLong(), any(), any())).thenReturn(new CompletableFuture<>());
    }

    @AfterEach
    void tearDown() {
        cleanup();
        removeAiConfig(0);
    }

    private void cleanup() {
        jdbc.update("delete from ai_task where tenant_id = 0 and skill_id = 'customer-background'");
        jdbc.update("delete from customer_background_report where tenant_id = 0");
        jdbc.update("delete from customer_inquiry where tenant_id = 0 and remark = 'it_bg'");
        jdbc.update("delete from customer_contact where tenant_id = 0");
        jdbc.update("delete from customer_field_source where tenant_id = 0");
        jdbc.update("delete from customer where tenant_id = 0");
    }

    private String token(String username) {
        String token = jwtUtils.generateToken(Map.of("tenantId", 0), username, 3600);
        redis.opsForValue().set(RedisKeyConstants.TOKEN_PREFIX + token, "1");
        return token;
    }

    private MockHttpServletResponse perform(MockHttpServletRequestBuilder r, String token) throws Exception {
        r.header("Authorization", "Bearer " + token);
        return mvc.perform(r).andReturn().getResponse();
    }

    private JsonNode call(MockHttpServletRequestBuilder r, String token) throws Exception {
        return objectMapper.readTree(perform(r, token).getContentAsString(StandardCharsets.UTF_8));
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder b, String body) {
        return b.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private JsonNode ok(JsonNode res) {
        assertEquals(0, res.path("code").asInt(), res.toString());
        return res.path("data");
    }

    private long customer() throws Exception {
        String body = "{\"name\":\"Pacific Controls Pty Ltd\",\"country\":\"Australia\",\"customerRole\":2,\"currency\":\"USD\","
                + "\"website\":\"https://pacificcontrols.com.au\",\"contactName\":\"James Walker\",\"whatsapp\":\"+61 400 111 222\"}";
        return ok(call(json(post(BASE), body), admin)).path("id").asLong();
    }

    private JsonNode research(long id, boolean confirmed) throws Exception {
        return call(json(post(BASE + "/" + id + "/background-reports/research"), "{\"confirmed\":" + confirmed + "}"), admin);
    }

    private void inquiry(long customerId, String code, LocalDate date, String content) {
        jdbc.update("insert into customer_inquiry (tenant_id, inquiry_code, customer_id, raw_content, inquiry_date, quote_deadline, remark) "
                + "values (0, ?, ?, ?, ?, ?, 'it_bg')", code, customerId, content, date, date);
    }

    @Test
    void research_sendsCustomerWithoutPhoneAndCreatesRunningReport() throws Exception {
        long id = customer();
        ok(call(json(post(BASE + "/" + id + "/contacts"), "{\"name\":\"Anna Lee\",\"title\":\"Purchasing Manager\","
                + "\"email\":\"anna@PacificControls.com.au\",\"phone\":\"+61 2 9999 8888\",\"whatsapp\":\"+61 411 222 333\"}"), admin));
        ok(call(json(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(BASE + "/" + id + "/profile"),
                "{\"fields\":[\"industry\",\"mainProducts\"],\"industry\":4,\"mainProducts\":\"Packaging machines\"}"), admin));
        inquiry(id, "ITBG1", LocalDate.now().minusDays(30), "oldest inquiry");
        inquiry(id, "ITBG2", LocalDate.now().minusDays(20), "x".repeat(5000));
        inquiry(id, "ITBG3", LocalDate.now().minusDays(10), "need 6ES7 214-1AG40-0XB0 x 5");
        inquiry(id, "ITBG4", LocalDate.now().minusDays(1), "latest inquiry");

        JsonNode report = ok(research(id, false));
        String today = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        assertTrue(report.path("reportNo").asText().startsWith("BG" + today), report.toString());
        assertEquals(1, report.path("status").asInt(), "背调中");
        assertEquals(1, report.path("source").asInt(), "AI 背调");
        Long taskId = jdbc.queryForObject("select ai_task_id from customer_background_report where id = ?", Long.class,
                report.path("id").asLong());
        assertEquals("customer-background", jdbc.queryForObject("select skill_id from ai_task where id = ?", String.class, taskId));

        ArgumentCaptor<String> captured = ArgumentCaptor.forClass(String.class);
        verify(orchestrator, timeout(5000)).submit(eq("customer-background"), eq(taskId), captured.capture(), any());
        String raw = captured.getValue();
        JsonNode in = objectMapper.readTree(raw);
        assertEquals("research", in.path("mode").asText());
        assertFalse(raw.contains("400 111 222") || raw.contains("411 222 333") || raw.contains("9999 8888"), "不发送电话与 WhatsApp");
        assertFalse(raw.toLowerCase().contains("whatsapp") || raw.contains("\"phone\""), raw);
        assertFalse(raw.contains("anna@"), "只发邮箱域名");

        JsonNode c = in.path("customer");
        assertEquals("Pacific Controls Pty Ltd", c.path("name").asText());
        assertEquals("https://pacificcontrols.com.au", c.path("website").asText());
        assertEquals("包装印刷", c.path("profile").path("行业").asText(), "选项型字段换成选项名");
        assertEquals("系统集成商", c.path("profile").path("公司类型").asText());
        assertEquals("Packaging machines", c.path("profile").path("主营产品").asText());
        assertEquals("没有成交", c.path("deals").path("totalRangeUsd").asText());
        JsonNode anna = null;
        for (JsonNode x : c.path("contacts")) {
            if ("Anna Lee".equals(x.path("name").asText())) {
                anna = x;
            }
        }
        assertEquals("pacificcontrols.com.au", anna.path("emailDomain").asText());
        assertEquals("Purchasing Manager", anna.path("title").asText());

        JsonNode inquiries = in.path("inquiries");
        assertEquals(3, inquiries.size(), "最近 3 张询盘");
        assertEquals("latest inquiry", inquiries.path(0).path("content").asText());
        assertEquals(3000, inquiries.path(2).path("content").asText().length(), "每张截取前 3000 字");

        boolean hasCompanySize = false;
        for (JsonNode f : in.path("fields")) {
            assertFalse("potentialRating".equals(f.path("key").asText()), "潜力评级由综合评分给出");
            if ("companySize".equals(f.path("key").asText())) {
                hasCompanySize = f.path("options").size() > 0;
            }
        }
        assertTrue(hasCompanySize, "选项型字段带上选项");
    }

    @Test
    void research_onlyOneRunningAtATime() throws Exception {
        long id = customer();
        ok(research(id, false));
        JsonNode second = research(id, true);
        assertEquals("BACKGROUND_RUNNING", second.path("data").path("errorCode").asText(), second.toString());
        assertEquals(1, jdbc.queryForObject("select count(*) from customer_background_report where customer_id = ?", Integer.class, id));
    }

    @Test
    void research_remindsWithin24HoursAndProceedsAfterConfirm() throws Exception {
        long id = customer();
        long first = ok(research(id, false)).path("id").asLong();
        jdbc.update("update customer_background_report set status = 2, create_time = date_sub(now(), interval 3 hour) where id = ?", first);

        JsonNode remind = research(id, false);
        assertEquals("BACKGROUND_RECENT", remind.path("data").path("errorCode").asText(), remind.toString());
        assertEquals("这个客户 3 小时前由IT背调过，仍要再背调吗？", remind.path("message").asText());
        assertEquals(1, jdbc.queryForObject("select count(*) from customer_background_report where customer_id = ?", Integer.class, id),
                "取消则不发起");

        ok(research(id, true));
        assertEquals(2, jdbc.queryForObject("select count(*) from customer_background_report where customer_id = ?", Integer.class, id));
    }

    @Test
    void research_failedOrOlderReportsDoNotRemind() throws Exception {
        long id = customer();
        long failed = ok(research(id, false)).path("id").asLong();
        jdbc.update("update customer_background_report set status = 3 where id = ?", failed);
        long old = ok(research(id, false)).path("id").asLong();
        jdbc.update("update customer_background_report set status = 2, create_time = date_sub(now(), interval 25 hour) where id = ?", old);
        ok(research(id, false));
    }

    @Test
    void research_requiresBackgroundPermission() throws Exception {
        long id = customer();
        loginWithResources("it_bg_sales", MENU_CUSTOMER);
        String sales = token("it_bg_sales");
        assertEquals(403, perform(json(post(BASE + "/" + id + "/background-reports/research"), "{}"), sales).getStatus());

        loginWithResources("it_bg_sales2", MENU_CUSTOMER, BTN_BACKGROUND);
        JsonNode res = call(json(post(BASE + "/" + id + "/background-reports/research"), "{}"), token("it_bg_sales2"));
        assertEquals(1, ok(res).path("status").asInt());
    }

    @Test
    void research_aiNotConfiguredCreatesNoReport() throws Exception {
        long id = customer();
        removeAiConfig(0);
        JsonNode res = research(id, false);
        assertEquals(AiConfigConstants.NOT_CONFIGURED_MESSAGE, res.path("message").asText(), res.toString());
        assertEquals(0, jdbc.queryForObject("select count(*) from customer_background_report where customer_id = ?", Integer.class, id));
    }
}
