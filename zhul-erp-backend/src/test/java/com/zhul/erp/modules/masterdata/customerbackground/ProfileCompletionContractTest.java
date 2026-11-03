package com.zhul.erp.modules.masterdata.customerbackground;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.common.constants.RedisKeyConstants;
import com.zhul.erp.common.utils.JwtUtils;
import com.zhul.erp.modules.aitask.service.AiOrchestratorClient;
import com.zhul.erp.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/** spec crm/customer-background「档案补全」：四个场景（一次补完一个、背调结果在向导里采纳、问了等回复、保存下一个）与左侧排序 */
@AutoConfigureMockMvc
class ProfileCompletionContractTest extends IntegrationTestBase {

    private static final String BASE = "/api/v1/masterdata/customers";
    private static final String QUEUE = "/api/v1/masterdata/customer-completion/queue";

    @Autowired private MockMvc mvc;
    @Autowired private JwtUtils jwtUtils;
    @Autowired private StringRedisTemplate redis;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private AiOrchestratorClient orchestrator;
    @Value("${zhul.ai-task.callback-token}") private String callbackToken;

    private String admin;

    @BeforeEach
    void setUp() {
        cleanup();
        seedAiConfig(0);
        loginAsAdmin("it_pc_admin");
        admin = token("it_pc_admin");
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
        jdbc.update("delete from customer_inquiry where tenant_id = 0 and remark = 'it_pc'");
        jdbc.update("delete from customer_contact where tenant_id = 0");
        jdbc.update("delete from customer_field_source where tenant_id = 0");
        jdbc.update("delete from country_profile where tenant_id = 0");
        jdbc.update("delete from customer where tenant_id = 0");
    }

    private String token(String username) {
        String token = jwtUtils.generateToken(Map.of("tenantId", 0), username, 3600);
        redis.opsForValue().set(RedisKeyConstants.TOKEN_PREFIX + token, "1");
        return token;
    }

    private JsonNode call(MockHttpServletRequestBuilder r) throws Exception {
        r.header("Authorization", "Bearer " + admin);
        return objectMapper.readTree(mvc.perform(r).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder b, String body) {
        return b.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private JsonNode ok(JsonNode res) {
        assertEquals(0, res.path("code").asInt(), res.toString());
        return res.path("data");
    }

    private long customer(String name) throws Exception {
        String body = "{\"name\":\"" + name + "\",\"country\":\"Australia\",\"customerRole\":2,\"currency\":\"USD\","
                + "\"website\":\"https://example.com.au\",\"contactName\":\"James Walker\"}";
        return ok(call(json(post(BASE), body))).path("id").asLong();
    }

    private void australiaProfile() {
        jdbc.update("insert into country_profile (tenant_id, country, voltages, fx_control, certifications) values (0, 'Australia', '[1]', 1, '[]')");
    }

    private JsonNode wizard(long id) throws Exception {
        return ok(call(get(BASE + "/" + id + "/completion")));
    }

    private static List<String> keys(JsonNode step) {
        List<String> out = new ArrayList<>();
        step.path("items").forEach(i -> out.add(i.path("key").asText()));
        return out;
    }

    private static JsonNode item(JsonNode step, String key) {
        for (JsonNode i : step.path("items")) {
            if (key.equals(i.path("key").asText())) {
                return i;
            }
        }
        return null;
    }

    @Test
    void wizard_groupsMissingItemsIntoFourSteps() throws Exception {
        australiaProfile();
        long id = customer("Pacific Controls Pty Ltd");
        ok(call(json(put(BASE + "/" + id + "/profile"), "{\"fields\":[\"industry\"],\"industry\":4}")));

        JsonNode w = wizard(id);
        JsonNode steps = w.path("steps");
        assertEquals(4, steps.size());
        assertEquals("系统已经知道", steps.path(0).path("name").asText());
        assertTrue(steps.path(0).path("autoFilled").toString().contains("电源电压（国家资料）"), steps.path(0).toString());
        assertFalse(steps.path(0).path("countryProfileMissing").asBoolean());

        List<String> research = keys(steps.path(1));
        assertTrue(research.containsAll(List.of("companySize", "mainProducts", "city", "jobRole")), research.toString());
        assertFalse(research.contains("industry"), "已经有值的不显示");

        List<String> judge = keys(steps.path(2));
        assertTrue(judge.containsAll(List.of("potentialRating", "qualityReq", "priceSensitivity", "commStyle")), judge.toString());
        assertFalse(judge.contains("customerRole"));

        JsonNode ask = steps.path(3);
        List<String> asks = keys(ask);
        assertTrue(asks.containsAll(List.of("designAbility", "protocols", "customsAbility", "paymentMethod", "decisionRole")), asks.toString());
        assertFalse(asks.contains("voltages"), "国家资料给出了电压");
        assertTrue(item(ask, "designAbility").path("question").asText().startsWith("Do you have your own engineers"));
        for (String k : asks) {
            assertFalse(item(ask, k).path("question").asText().isBlank(), "配置键用连字符（如 decision-role），也要对上字段：" + k);
        }
        long contactId = w.path("contactId").asLong();
        assertEquals("contact." + contactId + ".decisionRole", item(ask, "decisionRole").path("sourceKey").asText());
        assertTrue(ask.path("copyAll").asText().contains("Which communication protocols"));
        assertTrue(steps.path(1).path("report").isMissingNode() || steps.path(1).path("report").isNull());
    }

    @Test
    void wizard_countryProfileMissing() throws Exception {
        long id = customer("No Country Profile Co");
        JsonNode steps = wizard(id).path("steps");
        assertTrue(steps.path(0).path("countryProfileMissing").asBoolean(), "提示去维护国家资料");
        assertTrue(keys(steps.path(3)).contains("voltages"), "没有系统推导时电压只能问客户");
    }

    @Test
    void wizard_showsFreshReportAndAdoptRemovesItem() throws Exception {
        australiaProfile();
        long id = customer("Pacific Controls Pty Ltd");
        long reportId = ok(call(json(post(BASE + "/" + id + "/background-reports/research"), "{}"))).path("id").asLong();
        long taskId = jdbc.queryForObject("select ai_task_id from customer_background_report where id = ?", Long.class, reportId);
        mvc.perform(post("/api/v1/ai-tasks/" + taskId + "/callback").header("X-AI-Callback-Token", callbackToken)
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"status":"success","output":{"conclusion":{"priority":2,"score":3,"mainConflict":"m","firstAction":"f",
                          "identity":"i","supply":"s","keyFinding":"k","obstacle":"o"},"reportMarkdown":"正文",
                         "suggestions":[{"module":1,"field":"companySize","value":"10–50 人","evidence":"LinkedIn"},
                                        {"module":2,"field":"mainProducts","value":"Packaging lines","evidence":"官网"}]}}"""));

        JsonNode report = wizard(id).path("steps").path(1).path("report");
        assertEquals(reportId, report.path("id").asLong(), "180 天内背调过的显示报告结论");
        assertEquals(3, report.path("suggestions").size());
        assertTrue(report.path("reportMarkdown").isMissingNode() || report.path("reportMarkdown").isNull(), "不带正文");

        ok(call(json(post(BASE + "/" + id + "/background-reports/" + reportId + "/suggestions/adopt"), "{\"indexes\":[0,1]}")));
        List<String> research = keys(wizard(id).path("steps").path(1));
        assertFalse(research.contains("companySize") || research.contains("mainProducts"), research.toString());

        jdbc.update("update customer_background_report set create_time = date_sub(now(), interval 181 day) where id = ?", reportId);
        JsonNode stale = wizard(id).path("steps").path(1).path("report");
        assertTrue(stale.isMissingNode() || stale.isNull(), "超过 180 天的不显示");
    }

    @Test
    void asked_marksDaysAndClearsWhenFilled() throws Exception {
        australiaProfile();
        long id = customer("Pacific Controls Pty Ltd");
        ok(call(json(post(BASE + "/" + id + "/profile/asked"), "{\"keys\":[\"designAbility\",\"protocols\"],\"asked\":true}")));

        JsonNode ask = wizard(id).path("steps").path(3);
        assertEquals(0, item(ask, "designAbility").path("askedDays").asInt());
        assertEquals("IT", item(ask, "designAbility").path("askedByName").asText());
        JsonNode row = ok(call(get(QUEUE))).path("records").path(0);
        assertEquals(0, row.path("askedDays").asInt(), "左侧标「已问 0 天」");

        jdbc.update("update customer_field_source set asked_at = date_sub(now(), interval 2 day) where customer_id = ?", id);
        assertEquals(2, ok(call(get(QUEUE))).path("records").path(0).path("askedDays").asInt(), "两天后显示「已问 2 天」");

        ok(call(json(put(BASE + "/" + id + "/profile"), "{\"fields\":[\"designAbility\"],\"designAbility\":1}")));
        assertEquals(0L, jdbc.queryForObject("select asked_by from customer_field_source where customer_id = ? and field_key = 'designAbility'",
                Long.class, id), "填上后这一项的提问记录清除");
        JsonNode after = wizard(id).path("steps").path(3);
        assertNull(item(after, "designAbility"));
        assertEquals(2, item(after, "protocols").path("askedDays").asInt(), "其他项仍在等回复");
    }

    @Test
    void save_completesCustomerAndCountsTodayDone() throws Exception {
        australiaProfile();
        long first = customer("Pacific Controls Pty Ltd");
        long second = customer("Second Co");
        jdbc.update("update customer set potential_rating = 5 where id = ?", first);
        assertEquals(first, ok(call(get(QUEUE))).path("records").path(0).path("customerId").asLong());
        assertEquals(0, ok(call(get(QUEUE))).path("todayDone").asInt());

        ok(call(json(put(BASE + "/" + first + "/profile"), "{\"fields\":[\"industry\",\"companySize\",\"potentialRating\",\"mainProducts\","
                + "\"qualityReq\",\"priceSensitivity\",\"customsAbility\",\"paymentMethod\",\"currentBrands\",\"designAbility\",\"protocols\","
                + "\"repairAbility\",\"city\",\"timezone\"],\"industry\":4,\"companySize\":2,\"potentialRating\":5,\"mainProducts\":\"PLC\","
                + "\"qualityReq\":1,\"priceSensitivity\":1,\"customsAbility\":1,\"paymentMethod\":1,\"currentBrands\":[{\"name\":\"Siemens\"}],"
                + "\"designAbility\":1,\"protocols\":[1],\"repairAbility\":1,\"city\":\"Sydney\",\"timezone\":\"Australia/Sydney\"}")));
        long contactId = ok(call(get(BASE + "/" + first + "/contacts"))).path(0).path("id").asLong();
        ok(call(json(put(BASE + "/" + first + "/contacts/" + contactId), "{\"name\":\"James Walker\",\"jobRole\":1,\"decisionRole\":1,\"commStyle\":1}")));
        assertEquals(5, wizard(first).path("done").asInt());

        JsonNode q = ok(call(get(QUEUE)));
        assertEquals(1, q.path("todayDone").asInt(), "今天已补完 1 个客户");
        assertEquals(1, q.path("total").asInt(), "补齐的客户不再出现在左侧");
        assertEquals(second, q.path("records").path(0).path("customerId").asLong(), "下一个客户");
    }

    @Test
    void queue_sortsDealtByGradeThenUndealtByPotentialAndActivity() throws Exception {
        long a = customer("A grade 80");
        long s = customer("S grade");
        long a2 = customer("A grade 90");
        long p5old = customer("Potential 5 old");
        long p5new = customer("Potential 5 recent");
        long p3 = customer("Potential 3");
        long disabled = customer("Disabled");
        jdbc.update("update customer set biz_grade = 2, biz_grade_score = 80 where id = ?", a);
        jdbc.update("update customer set biz_grade = 1, biz_grade_score = 60 where id = ?", s);
        jdbc.update("update customer set biz_grade = 2, biz_grade_score = 90 where id = ?", a2);
        jdbc.update("update customer set potential_rating = 5 where id in (?, ?)", p5old, p5new);
        jdbc.update("update customer set potential_rating = 3 where id = ?", p3);
        jdbc.update("update customer set status = 0 where id = ?", disabled);
        jdbc.update("insert into customer_inquiry (tenant_id, inquiry_code, customer_id, raw_content, inquiry_date, quote_deadline, remark, update_time) "
                + "values (0, 'ITPC1', ?, 'x', curdate(), curdate(), 'it_pc', date_sub(now(), interval 30 day))", p5old);
        jdbc.update("insert into customer_inquiry (tenant_id, inquiry_code, customer_id, raw_content, inquiry_date, quote_deadline, remark, update_time) "
                + "values (0, 'ITPC2', ?, 'x', curdate(), curdate(), 'it_pc', now())", p5new);

        JsonNode q = ok(call(get(QUEUE).param("pageSize", "10")));
        List<Long> order = new ArrayList<>();
        q.path("records").forEach(r -> order.add(r.path("customerId").asLong()));
        assertEquals(List.of(s, a2, a, p5new, p5old, p3), order, "成交客户按 S、A、B、C 与综合分数在前；未成交按潜力与最近活动");
        assertEquals(6, q.path("total").asInt(), "停用的客户不在范围内");
        JsonNode first = q.path("records").path(0);
        assertEquals(1, first.path("bizGrade").asInt());
        assertTrue(first.path("missingCount").asInt() > 0);
        assertFalse(q.path("records").path(3).path("lastActivity").isNull(), "最近活动");
    }
}
