package com.zhul.erp.modules.crm;

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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * 商机接口契约（add-opportunity-management）：走完整的 HTTP + JWT + 权限 + 数据权限链路，连测试库执行真实 SQL。
 * 测试账号在平台租户（tenant 0）；非管理员没有配置数据权限，范围为「仅本人」。
 */
@AutoConfigureMockMvc
class OpportunityApiContractTest extends IntegrationTestBase {

    private static final String BASE = "/api/v1/crm/opportunities";
    private static final long SELF = 99000002L;
    private static final long OTHER_OWNER = 99000021L;
    private static final int MENU = 100053;
    private static final int MENU_STATS = 100071;
    private static final int RES_ADD = 110161;
    private static final int RES_EDIT = 110162;

    @Autowired private MockMvc mvc;
    @Autowired private JwtUtils jwtUtils;
    @Autowired private StringRedisTemplate redis;
    @Autowired private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        cleanup();
        jdbc.update("insert into user_basic (id, tenant_id, name, username, role_code, status) values (?, 0, '李娜', 'it_lina', '', 1)",
                OTHER_OWNER);
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    private void cleanup() {
        jdbc.update("delete from customer_inquiry where tenant_id = 0");
        jdbc.update("delete from opportunity_attachment where tenant_id = 0");
        jdbc.update("delete from opportunity_stage_log where tenant_id = 0");
        jdbc.update("delete from opportunity where tenant_id = 0");
        jdbc.update("delete from customer where tenant_id = 0");
        jdbc.update("delete from user_basic where id = ?", OTHER_OWNER);
    }

    private String token(String username) {
        String token = jwtUtils.generateToken(Map.of("tenantId", 0), username, 3600);
        redis.opsForValue().set(RedisKeyConstants.TOKEN_PREFIX + token, "1");
        return token;
    }

    private MockHttpServletResponse perform(MockHttpServletRequestBuilder request, String token) throws Exception {
        request.header("Authorization", "Bearer " + token);
        return mvc.perform(request).andReturn().getResponse();
    }

    private JsonNode call(MockHttpServletRequestBuilder request, String token) throws Exception {
        return objectMapper.readTree(perform(request, token).getContentAsString(StandardCharsets.UTF_8));
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder b, String body) {
        return b.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private long register(String token, String body) throws Exception {
        JsonNode res = call(json(post(BASE), body), token);
        assertEquals(0, res.path("code").asInt(), res.toString());
        return res.path("data").path("id").asLong();
    }

    private static String lead(String contact, int channel, String date, String extra) {
        return "{\"contactName\":\"" + contact + "\",\"country\":\"germany\",\"sourceChannel\":" + channel
                + ",\"firstContactDate\":\"" + date + "\"" + extra + "}";
    }

    private JsonNode ok(JsonNode res) {
        assertEquals(0, res.path("code").asInt(), res.toString());
        return res.path("data");
    }

    @Test
    void register_namelessSocialLead_thenDuplicateWhatsappBlocked() throws Exception {
        loginAsAdmin("opp_admin");
        String admin = token("opp_admin");
        String today = LocalDate.now().toString();
        long id = register(admin, lead("John", 5, today, ",\"whatsapp\":\"+49 151 2345 6789\",\"demandSummary\":\"S7-1200 × 50\""));

        JsonNode detail = ok(call(get(BASE + "/" + id), admin));
        assertEquals("John", detail.path("customerName").asText());
        assertTrue(detail.path("customerNameMissing").asBoolean());
        assertEquals("S1", detail.path("stageCode").asText());
        assertEquals("Germany", detail.path("country").asText());
        assertEquals(1, detail.path("stageLogs").size());
        assertTrue(detail.path("opportunityCode").asText().startsWith("OPP"));
        assertEquals("", jdbc.queryForObject("select name from customer where id = ?", String.class,
                detail.path("customerId").asLong()));

        JsonNode dup = call(json(post(BASE), lead("Johnny", 1, today, ",\"whatsapp\":\"0049-151-23456789\"")), admin);
        assertEquals("CUSTOMER_DUPLICATE", dup.path("data").path("errorCode").asText(), dup.toString());
        assertEquals("该客户已存在（John，负责人 IT），老客户的新需求请新建询盘", dup.path("message").asText());
        assertEquals(1, jdbc.queryForObject("select count(*) from opportunity where tenant_id = 0", Integer.class));

        String tomorrow = LocalDate.now().plusDays(1).toString();
        assertEquals(400, perform(json(post(BASE), lead("Anna", 5, tomorrow, "")), admin).getStatus());
        assertEquals(400, perform(json(post(BASE), "{\"country\":\"germany\",\"sourceChannel\":5,\"firstContactDate\":\""
                + today + "\"}"), admin).getStatus(), "联系人名称必填");
    }

    @Test
    void stageFlow_invalidLostWonReopen_andStats() throws Exception {
        loginAsAdmin("opp_admin");
        String admin = token("opp_admin");
        String day = LocalDate.now().minusDays(1).toString();
        long a = register(admin, lead("A", 2, day, ",\"email\":\"a@x.com\""));
        long b = register(admin, lead("B", 2, day, ",\"email\":\"b@x.com\""));
        long c = register(admin, lead("C", 1, day, ",\"email\":\"c@x.com\""));

        // A：S2 标无效
        ok(call(json(post(BASE + "/" + a + "/stage"), "{\"toStage\":\"S2\"}"), admin));
        ok(call(json(post(BASE + "/" + a + "/close"), "{\"result\":\"INVALID\",\"reason\":1,\"note\":\"只要报价单\"}"), admin));
        // B：推进到 S4，不能标无效；输单后重新打开回到 S4
        ok(call(json(post(BASE + "/" + b + "/stage"), "{\"toStage\":\"S4\"}"), admin));
        assertEquals("S3 及以后请标记为输单",
                call(json(post(BASE + "/" + b + "/close"), "{\"result\":\"INVALID\",\"reason\":1}"), admin).path("message").asText());
        assertEquals("请先推进到 S7 成交推进",
                call(json(post(BASE + "/" + b + "/close"), "{\"result\":\"WON\"}"), admin).path("message").asText());
        ok(call(json(post(BASE + "/" + b + "/close"), "{\"result\":\"LOST\",\"reason\":15}"), admin));
        ok(call(post(BASE + "/" + b + "/reopen"), admin));
        JsonNode bd = ok(call(get(BASE + "/" + b), admin));
        assertEquals("S4", bd.path("stageCode").asText());
        assertEquals(4, bd.path("stageLogs").size(), "登记、推进、输单、重新打开");
        ok(call(json(post(BASE + "/" + b + "/close"), "{\"result\":\"LOST\",\"reason\":11}"), admin));
        // C：推进到 S7 赢单
        ok(call(json(post(BASE + "/" + c + "/stage"), "{\"toStage\":\"S7\"}"), admin));
        ok(call(json(post(BASE + "/" + c + "/close"), "{\"result\":\"WON\"}"), admin));

        JsonNode stats = ok(call(get(BASE + "/stats").param("from", day).param("to", day).param("groupBy", "channel"), admin));
        JsonNode mic = stats.path("rows").get(0);
        assertEquals("中国制造网", mic.path("label").asText());
        assertEquals(2, mic.path("total").asInt());
        assertEquals(1, mic.path("invalid").asInt());
        assertEquals(1, mic.path("valid").asInt(), "输单的 B 进入过 S4，仍计为有效");
        assertEquals(1, mic.path("lost").asInt());
        JsonNode sum = stats.path("summary");
        assertEquals(3, sum.path("total").asInt());
        assertEquals(2, sum.path("valid").asInt());
        assertEquals(1, sum.path("won").asInt());

        JsonNode byDate = ok(call(get(BASE + "/stats").param("from", day).param("to", day).param("groupBy", "date"), admin));
        assertEquals(day, byDate.path("rows").get(0).path("label").asText());

        JsonNode page = ok(call(get(BASE + "/page").param("stage", "CLOSED"), admin));
        assertEquals(3, page.path("total").asInt());
        assertEquals(1, ok(call(get(BASE + "/page").param("keyword", "b@x"), admin)).path("total").asInt());
    }

    @Test
    void selfScope_andPermissions() throws Exception {
        loginAsAdmin("opp_admin");
        String admin = token("opp_admin");
        String today = LocalDate.now().toString();
        long own = register(admin, lead("Own", 5, today, ",\"email\":\"own@x.com\""));
        long others = register(admin, lead("Others", 5, today, ",\"email\":\"others@x.com\""));
        jdbc.update("update opportunity set owner_id = ? where id = ?", SELF, own);
        jdbc.update("update opportunity set owner_id = ? where id = ?", OTHER_OWNER, others);

        loginWithResources("opp_staff", MENU, MENU_STATS);
        String staff = token("opp_staff");
        JsonNode page = ok(call(get(BASE + "/page"), staff));
        assertEquals(1, page.path("total").asInt());
        assertEquals(own, page.path("records").get(0).path("id").asLong());
        assertEquals("商机不存在", call(get(BASE + "/" + others), staff).path("message").asText());
        assertEquals(1, ok(call(get(BASE + "/stats").param("from", today).param("to", today), staff)).path("summary").path("total").asInt());
        assertEquals(403, perform(json(post(BASE), lead("X", 5, today, "")), staff).getStatus(), "没有登记按钮权限");
        assertEquals(403, perform(json(post(BASE + "/" + own + "/stage"), "{\"toStage\":\"S2\"}"), staff).getStatus());

        loginWithResources("opp_nomenu", RES_EDIT);
        assertEquals(403, perform(get(BASE + "/page"), token("opp_nomenu")).getStatus(), "查看要求能访问商机列表菜单");

        loginWithResources("opp_liststaff", MENU);
        assertEquals(403, perform(get(BASE + "/stats").param("from", today).param("to", today), token("opp_liststaff")).getStatus(),
                "统计要求能访问商机统计菜单");
    }

    @Test
    void attachments_uploadRegisterDownload() throws Exception {
        loginAsAdmin("opp_admin");
        String admin = token("opp_admin");
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};
        JsonNode pdf = call(multipart(BASE + "/attachments").file(new MockMultipartFile("file", "需求.pdf",
                "application/pdf", "%PDF-1.4".getBytes(StandardCharsets.US_ASCII))), admin);
        assertEquals("只支持图片（JPG、PNG）和 Excel", pdf.path("message").asText());
        JsonNode up = ok(call(multipart(BASE + "/attachments").file(new MockMultipartFile("file", "询价.png", "image/png", png)), admin));
        String key = up.path("fileKey").asText();
        assertTrue(key.startsWith("opportunity/0/"), key);

        long id = register(admin, lead("Pic", 5, LocalDate.now().toString(), ",\"email\":\"pic@x.com\",\"attachments\":[{\"fileName\":\"询价.png\",\"fileKey\":\""
                + key + "\"}]"));
        JsonNode att = ok(call(get(BASE + "/" + id), admin)).path("attachments").get(0);
        assertEquals("询价.png", att.path("fileName").asText());
        assertFalse(att.has("fileKey"));
        MockHttpServletResponse dl = perform(get(BASE + "/" + id + "/attachments/" + att.path("id").asLong()), admin);
        assertEquals(200, dl.getStatus());
        assertEquals("image/png", dl.getContentType());
        assertEquals(png.length, dl.getContentAsByteArray().length);

        JsonNode forged = call(json(put(BASE + "/" + id), "{\"sourceChannel\":5,\"firstContactDate\":\"" + LocalDate.now()
                + "\",\"attachments\":[{\"fileKey\":\"supplier/0/202609/" + "a".repeat(32) + ".png\"}]}"), admin);
        assertEquals("附件不存在，请重新上传", forged.path("message").asText());
    }

    @Test
    void createInquiryFromOpportunity_linksBothWays() throws Exception {
        loginAsAdmin("opp_admin");
        String admin = token("opp_admin");
        String key = ok(call(multipart(BASE + "/attachments").file(new MockMultipartFile("file", "bom.csv", "text/csv",
                "model,qty\n6ES7,50".getBytes(StandardCharsets.UTF_8))), admin)).path("fileKey").asText();
        long id = register(admin, lead("Conv", 5, LocalDate.now().toString(), ",\"email\":\"conv@x.com\",\"attachments\":[{\"fileName\":\"bom.csv\",\"fileKey\":\""
                + key + "\"}]"));
        JsonNode opp = ok(call(get(BASE + "/" + id), admin));
        long customerId = opp.path("customerId").asLong();
        long attId = opp.path("attachments").get(0).path("id").asLong();

        String inquiryBase = "/api/v1/inquiry/customer-inquiries";
        JsonNode copied = ok(call(json(post(inquiryBase + "/attachments/from-opportunity"),
                "{\"opportunityId\":" + id + ",\"attachmentId\":" + attId + "}"), admin));
        assertTrue(copied.path("url").asText().startsWith("/uploads/customer-inquiry/"), copied.toString());

        JsonNode wrong = call(json(post(inquiryBase), "{\"customerId\":" + (customerId + 999) + ",\"source\":2,\"opportunityId\":" + id + "}"), admin);
        assertFalse(wrong.path("code").asInt() == 0, "客户不存在或与商机不一致都应拒绝");

        long inquiryId = ok(call(json(post(inquiryBase), "{\"customerId\":" + customerId + ",\"source\":2,\"rawAttachmentUrl\":\""
                + copied.path("url").asText() + "\",\"opportunityId\":" + id + "}"), admin)).path("id").asLong();
        JsonNode inquiry = ok(call(get(inquiryBase + "/" + inquiryId), admin));
        assertEquals(opp.path("opportunityCode").asText(), inquiry.path("opportunityCode").asText());
        assertEquals("新商机", inquiry.path("opportunityStageName").asText());
        assertEquals(inquiryId, ok(call(get(BASE + "/" + id), admin)).path("inquiries").get(0).path("id").asLong());
        assertEquals("S1", ok(call(get(BASE + "/" + id), admin)).path("stageCode").asText(), "转询盘不自动改阶段");
    }
}
