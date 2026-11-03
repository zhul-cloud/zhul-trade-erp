package com.zhul.erp.modules.masterdata.customerbackground;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.common.constants.RedisKeyConstants;
import com.zhul.erp.common.utils.JwtUtils;
import com.zhul.erp.modules.aitask.constants.AiConfigConstants;
import com.zhul.erp.modules.aitask.service.AiOrchestratorClient;
import com.zhul.erp.modules.document.support.DocumentConverter;
import com.zhul.erp.modules.masterdata.customerbackground.support.BackgroundReportHtml;
import com.zhul.erp.support.IntegrationTestBase;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * spec crm/customer-background：「一键 AI 背调」（输入组装、同时只能一份背调中、24 小时提醒、权限、AI 未配置）、
 * 「上传背调报告」（PDF、粘贴文字、扫描件）、「背调报告」（回调保存、查询、详情、删除、导出 PDF）、「字段建议与采纳」。
 */
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
    @Autowired private DocumentConverter converter;
    @Value("${zhul.ai-task.callback-token}") private String callbackToken;

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
        jdbc.update("update customer_background_report set status = 2, priority = 1, score = 4, input_tokens = 128000, web_searches = 18,"
                + " create_time = date_sub(now(), interval 3 hour) where id = ?", first);

        JsonNode remind = research(id, false);
        assertEquals("BACKGROUND_RECENT", remind.path("data").path("errorCode").asText(), remind.toString());
        assertEquals("这个客户 3 小时前由IT背调过，仍要再背调吗？", remind.path("message").asText());
        JsonNode last = remind.path("data").path("detail");
        assertEquals(first, last.path("reportId").asLong(), "提醒里带上次报告，供「先看上次的报告」");
        assertEquals(1, last.path("priority").asInt());
        assertEquals(4, last.path("score").asInt());
        assertEquals(128000, last.path("inputTokens").asInt());
        assertEquals(18, last.path("webSearches").asInt());
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

    // ---------------------------------------------------------------- 上传报告（4.3）

    private static byte[] pdf(String... lines) throws IOException {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            if (lines.length > 0) {
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.beginText();
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                    cs.newLineAtOffset(50, 700);
                    for (String line : lines) {
                        cs.showText(line);
                        cs.newLineAtOffset(0, -16);
                    }
                    cs.endText();
                }
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    private JsonNode upload(long id, MockMultipartFile file, String text) throws Exception {
        var b = multipart(BASE + "/" + id + "/background-reports/upload");
        if (file != null) {
            b.file(file);
        }
        if (text != null) {
            b.param("text", text);
        }
        return call(b, admin);
    }

    private long reportCount(long id) {
        return jdbc.queryForObject("select count(*) from customer_background_report where customer_id = ?", Long.class, id);
    }

    @Test
    void upload_pdfExtractsTextWithoutWebAndKeepsOriginal() throws Exception {
        long id = customer();
        byte[] bytes = pdf("Pacific Controls Pty Ltd - Background Report", "Score: 4 / 5. Employees: 23 on LinkedIn.");
        JsonNode r = ok(upload(id, new MockMultipartFile("file", "Pacific Controls_20261103_4分.pdf", "application/pdf", bytes), null));
        assertEquals(2, r.path("source").asInt(), "来源为上传");
        assertEquals(1, r.path("status").asInt());

        ArgumentCaptor<String> captured = ArgumentCaptor.forClass(String.class);
        verify(orchestrator, timeout(5000)).submit(eq("customer-background"), anyLong(), captured.capture(), any());
        JsonNode in = objectMapper.readTree(captured.getValue());
        assertEquals("extract", in.path("mode").asText(), "只提取，不联网");
        assertTrue(in.path("reportText").asText().contains("Employees: 23 on LinkedIn."));
        assertTrue(in.path("customer").isMissingNode(), "提取模式不需要客户资料");

        JsonNode detail = ok(call(get(BASE + "/" + id + "/background-reports/" + r.path("id").asLong()), admin));
        assertTrue(detail.path("reportMarkdown").asText().startsWith("Pacific Controls Pty Ltd - Background Report"), "正文为上传的文字");
        assertEquals("Pacific Controls_20261103_4分.pdf", detail.path("uploadFileName").asText());
        JsonNode link = ok(call(get(BASE + "/" + id + "/background-reports/" + r.path("id").asLong() + "/file-url"), admin));
        assertFalse(link.path("url").asText().isEmpty(), "可以下载原 PDF");
    }

    @Test
    void upload_scannedPdfIsRejected() throws Exception {
        long id = customer();
        JsonNode res = upload(id, new MockMultipartFile("file", "scan.pdf", "application/pdf", pdf()), null);
        assertEquals("这个 PDF 没有可读文字，请粘贴报告文字", res.path("message").asText(), res.toString());
        assertEquals(0, reportCount(id));
    }

    @Test
    void upload_checksFileTypeAndTextLength() throws Exception {
        long id = customer();
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        assertEquals("只支持 PDF 文件", upload(id, new MockMultipartFile("file", "fake.pdf", "application/pdf", png), null)
                .path("message").asText(), "按文件头校验");
        assertEquals("请上传一个 PDF，或粘贴报告文字", upload(id, null, null).path("message").asText());
        assertEquals("报告文字不能超过 60000 字", upload(id, null, "字".repeat(60001)).path("message").asText());
        assertEquals(0, reportCount(id));

        JsonNode r = ok(upload(id, null, "  Pacific Controls is a system integrator in Sydney.  "));
        assertEquals(2, r.path("source").asInt());
        assertEquals("Pacific Controls is a system integrator in Sydney.",
                jdbc.queryForObject("select report_markdown from customer_background_report where id = ?", String.class, r.path("id").asLong()));
    }

    @Test
    void upload_alsoOneRunningAtATime() throws Exception {
        long id = customer();
        ok(research(id, false));
        JsonNode res = upload(id, null, "some report text for the customer");
        assertEquals("BACKGROUND_RUNNING", res.path("data").path("errorCode").asText(), res.toString());
    }

    // ---------------------------------------------------------------- 回调保存、查询、详情、删除（4.4）

    private long taskOf(long reportId) {
        return jdbc.queryForObject("select ai_task_id from customer_background_report where id = ?", Long.class, reportId);
    }

    private void callback(long taskId, String body) throws Exception {
        int status = mvc.perform(post("/api/v1/ai-tasks/" + taskId + "/callback")
                        .header("X-AI-Callback-Token", callbackToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getStatus();
        assertEquals(200, status);
    }

    private static final String RESULT = """
            {"status":"success","usage":{"model":"claude-sonnet-5-5","inputTokens":42000,"outputTokens":6100,"webSearches":9},
             "output":{"conclusion":{"priority":1,"score":4,"mainConflict":"有真实项目但已有稳定供应商","firstAction":"先发西门子 S7-1200 现货清单",
               "identity":"悉尼的系统集成商，23 人","supply":"主要从本地代理拿货","keyFinding":"官网有 3 个包装线改造案例","obstacle":"账期要求 30 天"},
              "reportMarkdown":"# Pacific Controls 背调报告\\n\\n正文",
              "suggestions":[
                {"module":1,"field":"companySize","value":"11-50人","evidence":"LinkedIn 显示 23 名员工","sourceUrl":"https://linkedin.com/company/pc"},
                {"module":1,"field":"companySize","value":"51-200人","evidence":"重复的字段","sourceUrl":""},
                {"module":1,"field":"potentialRating","value":"5","evidence":"AI 自己给的潜力不用"},
                {"module":4,"field":"phone","value":"+61 2 9999 8888","evidence":"不允许的字段"},
                {"module":4,"field":"jobRole","value":"采购","evidence":"LinkedIn 职位","sourceUrl":"",
                 "contact":{"name":"Anna Lee","email":"anna@pacificcontrols.com.au","title":"Purchasing Manager"}}]}}""";

    @Test
    void callback_savesConclusionSuggestionsAndUsage() throws Exception {
        long id = customer();
        long reportId = ok(research(id, false)).path("id").asLong();
        callback(taskOf(reportId), RESULT);

        JsonNode d = ok(call(get(BASE + "/" + id + "/background-reports/" + reportId), admin));
        assertEquals(2, d.path("status").asInt(), "已完成");
        assertEquals(1, d.path("priority").asInt());
        assertEquals(4, d.path("score").asInt());
        assertEquals("先发西门子 S7-1200 现货清单", d.path("firstAction").asText());
        assertEquals("悉尼的系统集成商，23 人", d.path("identityNote").asText());
        assertEquals("# Pacific Controls 背调报告\n\n正文", d.path("reportMarkdown").asText());
        assertEquals("<h1>Pacific Controls 背调报告</h1>\n<p>正文</p>\n", d.path("reportHtml").asText(), "页面直接展示渲染好的正文");
        assertEquals("claude-sonnet-5-5", d.path("model").asText());
        assertEquals(42000, d.path("inputTokens").asInt());
        assertEquals(6100, d.path("outputTokens").asInt());
        assertEquals(9, d.path("webSearches").asInt());
        assertTrue(d.path("durationSeconds").isInt());
        assertFalse(d.path("completedAt").isNull());

        JsonNode s = d.path("suggestions");
        assertEquals(3, s.size(), "去掉重复字段、AI 给的潜力评级和不允许的字段，加上综合评分：" + s);
        assertEquals("companySize", s.path(0).path("field").asText());
        assertEquals("11-50人", s.path(0).path("value").asText(), "同一字段只留第一条");
        assertEquals("pending", s.path(0).path("state").asText());
        assertEquals("jobRole", s.path(1).path("field").asText());
        assertEquals(4, s.path(1).path("module").asInt());
        assertEquals("Anna Lee", s.path(1).path("contact").path("name").asText());
        assertEquals("potentialRating", s.path(2).path("field").asText(), "综合评分同时作为潜力评级的建议");
        assertEquals("4", s.path(2).path("value").asText());
        assertEquals(3, d.path("suggestionCount").asInt());
    }

    @Test
    void callback_uploadKeepsOriginalTextAndRecordsZeroSearches() throws Exception {
        long id = customer();
        long reportId = ok(upload(id, null, "Original uploaded report text")).path("id").asLong();
        callback(taskOf(reportId), RESULT.replace("\"webSearches\":9", "\"webSearches\":0"));
        Map<String, Object> row = jdbc.queryForMap("select status, report_markdown, web_searches from customer_background_report where id = ?", reportId);
        assertEquals(2, ((Number) row.get("status")).intValue());
        assertEquals("Original uploaded report text", row.get("report_markdown"), "上传的报告正文保持原文");
        assertEquals(0, ((Number) row.get("web_searches")).intValue(), "联网次数为 0");
    }

    @Test
    void callback_failureMarksReportFailedAndAllowsRetry() throws Exception {
        long id = customer();
        long reportId = ok(research(id, false)).path("id").asLong();
        callback(taskOf(reportId), "{\"status\":\"failed\",\"error\":\"AI 处理超过 600 秒未完成\"}");
        Map<String, Object> row = jdbc.queryForMap("select status, fail_reason from customer_background_report where id = ?", reportId);
        assertEquals(3, ((Number) row.get("status")).intValue(), "失败");
        assertEquals("AI 处理超过 600 秒未完成", row.get("fail_reason"));
        ok(research(id, false));
    }

    @Test
    void callback_unreadableOutputMarksFailed() throws Exception {
        long id = customer();
        long reportId = ok(research(id, false)).path("id").asLong();
        callback(taskOf(reportId), "{\"status\":\"success\",\"output\":{\"foo\":1}}");
        assertEquals(3, jdbc.queryForObject("select status from customer_background_report where id = ?", Integer.class, reportId));
    }

    @Test
    void list_newestFirstAndOnlyThisCustomer() throws Exception {
        long id = customer();
        long first = ok(research(id, false)).path("id").asLong();
        callback(taskOf(first), RESULT);
        jdbc.update("update customer_background_report set create_time = date_sub(now(), interval 2 day) where id = ?", first);
        long second = ok(research(id, false)).path("id").asLong();

        JsonNode list = ok(call(get(BASE + "/" + id + "/background-reports"), admin));
        assertEquals(2, list.size());
        assertEquals(second, list.path(0).path("id").asLong(), "按时间倒序");
        assertEquals("IT", list.path(0).path("requestedByName").asText());
        assertEquals(4, list.path(1).path("score").asInt());

        long other = ok(call(json(post(BASE), "{\"name\":\"Other Co\",\"country\":\"Brazil\",\"customerRole\":1,\"currency\":\"USD\"}"), admin))
                .path("id").asLong();
        assertEquals("背调报告不存在或已删除",
                call(get(BASE + "/" + other + "/background-reports/" + first), admin).path("message").asText(), "报告不属于这个客户");
    }

    @Test
    void delete_byRequesterOrManagerOnly_andLateResultIgnored() throws Exception {
        long id = customer();
        long reportId = ok(research(id, false)).path("id").asLong();

        // 只有查看客户权限、数据范围只有自己的业务员不能删别人的报告
        jdbc.update("update customer_background_report set requested_by = 99000077 where id = ?", reportId);
        jdbc.update("update customer set owner_id = 99000002 where id = ?", id);
        loginWithResources("it_bg_viewer", MENU_CUSTOMER);
        String viewer = token("it_bg_viewer");
        JsonNode denied = call(delete(BASE + "/" + id + "/background-reports/" + reportId), viewer);
        assertEquals("只有发起人或管理者可以删除这份报告", denied.path("message").asText(), denied.toString());

        loginAsAdmin("it_bg_admin");
        ok(call(delete(BASE + "/" + id + "/background-reports/" + reportId), admin));
        assertEquals(0, ok(call(get(BASE + "/" + id + "/background-reports"), admin)).size());
        assertEquals(1, reportCount(id), "软删除");

        callback(taskOf(reportId), RESULT);
        assertEquals(1, jdbc.queryForObject("select status from customer_background_report where id = ?", Integer.class, reportId),
                "删除后回来的结果不再写入");
        ok(research(id, false));
    }

    // ---------------------------------------------------------------- 字段建议与采纳（4.5）

    private static final String ADOPT_RESULT = """
            {"status":"success","output":{"conclusion":{"priority":2,"score":4,"mainConflict":"m","firstAction":"f",
               "identity":"i","supply":"s","keyFinding":"k","obstacle":"o"},"reportMarkdown":"正文",
             "suggestions":[
              {"module":1,"field":"companySize","value":"10–50 人","evidence":"LinkedIn 显示 23 名员工","sourceUrl":"https://linkedin.com/company/pc"},
              {"module":1,"field":"industry","value":"包装印刷","evidence":"官网案例都是包装线"},
              {"module":3,"field":"protocols","value":"Modbus、Profinet IRT","evidence":"招聘启事"},
              {"module":4,"field":"jobRole","value":"采购","evidence":"LinkedIn 职位",
               "contact":{"name":"Anna Lee","email":"ANNA@pacificcontrols.com.au","title":"Purchasing Manager"}},
              {"module":4,"field":"decisionRole","value":"拍板人","evidence":"官网写明是创始人",
               "contact":{"name":"Bob Smith","email":"bob@pacificcontrols.com.au","title":"Managing Director"}},
              {"module":4,"field":"jobRole","value":"工程师","evidence":"没有姓名的联系人",
               "contact":{"name":"","email":"eng@pacificcontrols.com.au","title":"Engineer"}},
              {"module":2,"field":"paymentMethod","value":"T/T 定金 + 发货前付尾款","evidence":"行业惯例"}]}}""";

    /** 客户行业已是「食品饮料」，联系人 Anna Lee；报告完成，返回报告 ID */
    private long[] doneReport() throws Exception {
        long id = customer();
        ok(call(json(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(BASE + "/" + id + "/profile"),
                "{\"fields\":[\"industry\"],\"industry\":3}"), admin));
        ok(call(json(post(BASE + "/" + id + "/contacts"), "{\"name\":\"Anna Lee\",\"email\":\"anna@pacificcontrols.com.au\"}"), admin));
        long reportId = ok(research(id, false)).path("id").asLong();
        callback(taskOf(reportId), ADOPT_RESULT);
        return new long[] {id, reportId};
    }

    private JsonNode suggestionsOf(long id, long reportId) throws Exception {
        return ok(call(get(BASE + "/" + id + "/background-reports/" + reportId), admin)).path("suggestions");
    }

    private JsonNode adopt(long id, long reportId, String indexes) throws Exception {
        return call(json(post(BASE + "/" + id + "/background-reports/" + reportId + "/suggestions/adopt"), "{\"indexes\":" + indexes + "}"), admin);
    }

    @Test
    void detail_marksCurrentValueDefaultCheckAndUnavailable() throws Exception {
        long[] x = doneReport();
        JsonNode s = suggestionsOf(x[0], x[1]);
        assertEquals(8, s.size(), "七条建议 + 综合评分作为潜力评级");
        assertEquals("公司规模", s.path(0).path("label").asText());
        assertTrue(s.path(0).path("defaultChecked").asBoolean(), "当前值为空的默认勾选");
        assertTrue(s.path(0).path("currentValue").isMissingNode() || s.path(0).path("currentValue").isNull());
        assertEquals("食品饮料", s.path(1).path("currentValue").asText());
        assertFalse(s.path(1).path("defaultChecked").asBoolean(), "已有值的不默认覆盖");
        assertEquals("无法采纳：选项不存在", s.path(2).path("unavailableReason").asText());
        assertFalse(s.path(2).path("defaultChecked").asBoolean());
        assertTrue(s.path(3).path("defaultChecked").asBoolean(), "匹配到 Anna Lee，职位类别为空");
        assertTrue(s.path(4).path("unavailableReason").isMissingNode(), "新联系人有姓名，可以采纳");
        assertEquals("无法采纳：联系人没有姓名", s.path(5).path("unavailableReason").asText());
        assertTrue(s.path(6).path("unavailableReason").asText().contains("定金比例或账期"));
        assertEquals("potentialRating", s.path(7).path("field").asText());
    }

    @Test
    void adopt_writesProfileAndContactsWithReportSource() throws Exception {
        long[] x = doneReport();
        long id = x[0];
        String reportNo = jdbc.queryForObject("select report_no from customer_background_report where id = ?", String.class, x[1]);
        ok(adopt(id, x[1], "[0,1,3,4,7]"));

        Map<String, Object> c = jdbc.queryForMap("select company_size, industry, potential_rating, profile_mask from customer where id = ?", id);
        assertEquals(2, ((Number) c.get("company_size")).intValue(), "公司规模为「10–50 人」");
        assertEquals(4, ((Number) c.get("industry")).intValue(), "勾选后覆盖为包装印刷");
        assertEquals(4, ((Number) c.get("potential_rating")).intValue());
        assertEquals(1, ((Number) c.get("profile_mask")).intValue() & 1, "完整度随之更新：基础画像已完成");

        JsonNode src = ok(call(get(BASE + "/" + id + "/profile"), admin)).path("sources").path("companySize");
        assertEquals(2, src.path("source").asInt(), "来源为背调报告");
        assertEquals(reportNo, src.path("reportNo").asText());
        assertEquals("IT", src.path("operatorName").asText());
        assertFalse(src.path("reportDeleted").asBoolean());

        assertEquals(1, jdbc.queryForObject("select job_role from customer_contact where customer_id = ? and name = 'Anna Lee' and deleted_at is null",
                Integer.class, id), "按邮箱（忽略大小写）匹配已有联系人并更新");
        Map<String, Object> bob = jdbc.queryForMap("select decision_role, title, email from customer_contact where customer_id = ? and name = 'Bob Smith'", id);
        assertEquals(1, ((Number) bob.get("decision_role")).intValue(), "找不到就新建联系人");
        assertEquals("Managing Director", bob.get("title"));
        assertEquals(1, jdbc.queryForObject("select count(*) from customer_contact where customer_id = ? and name = 'Anna Lee' and deleted_at is null",
                Integer.class, id), "不重复建联系人");

        JsonNode s = suggestionsOf(id, x[1]);
        assertEquals("adopted", s.path(0).path("state").asText());
        assertEquals("IT", s.path(0).path("operatorName").asText());
        assertFalse(s.path(0).path("at").isMissingNode());
        assertEquals("pending", s.path(2).path("state").asText());
        assertTrue(jdbc.queryForObject("select count(*) from sys_log where operation = '采纳背调建议' and operate_time >= date_sub(now(), interval 5 minute)", Integer.class) > 0, "写操作日志");

        assertEquals("「公司规模」已经采纳过", adopt(id, x[1], "[0]").path("message").asText());

        // 删除报告后，字段来源仍显示原报告编号，并标「报告已删除」
        ok(call(delete(BASE + "/" + id + "/background-reports/" + x[1]), admin));
        JsonNode after = ok(call(get(BASE + "/" + id + "/profile"), admin)).path("sources").path("companySize");
        assertEquals(reportNo, after.path("reportNo").asText());
        assertTrue(after.path("reportDeleted").asBoolean());
        assertEquals(2, jdbc.queryForObject("select company_size from customer where id = ?", Integer.class, id), "已采纳的字段不受影响");
    }

    @Test
    void adopt_unavailableRejectsWholeBatch() throws Exception {
        long[] x = doneReport();
        JsonNode res = adopt(x[0], x[1], "[0,2]");
        assertEquals("「常用通讯协议」无法采纳：选项不存在", res.path("message").asText(), res.toString());
        assertEquals(0, jdbc.queryForObject("select company_size from customer where id = ?", Integer.class, x[0]), "整批不写入");
        assertEquals("「职位类别」无法采纳：联系人没有姓名", adopt(x[0], x[1], "[5]").path("message").asText());
        assertEquals("建议不存在，请刷新后重试", adopt(x[0], x[1], "[99]").path("message").asText());
    }

    @Test
    void ignore_marksIgnoredAndCannotIgnoreAdopted() throws Exception {
        long[] x = doneReport();
        String url = BASE + "/" + x[0] + "/background-reports/" + x[1] + "/suggestions/ignore";
        ok(call(json(post(url), "{\"indexes\":[2,6]}"), admin));
        JsonNode s = suggestionsOf(x[0], x[1]);
        assertEquals("ignored", s.path(2).path("state").asText());
        assertEquals("ignored", s.path(6).path("state").asText());

        ok(adopt(x[0], x[1], "[0]"));
        assertEquals("「公司规模」已经采纳，不能忽略", call(json(post(url), "{\"indexes\":[0]}"), admin).path("message").asText());
    }

    @Test
    void adopt_requiresDoneReportAndEditPermission() throws Exception {
        long id = customer();
        long reportId = ok(research(id, false)).path("id").asLong();
        assertEquals("报告还没有完成，不能处理字段建议", adopt(id, reportId, "[0]").path("message").asText());

        callback(taskOf(reportId), ADOPT_RESULT);
        loginWithResources("it_bg_noedit", MENU_CUSTOMER, BTN_BACKGROUND);
        assertEquals(403, perform(json(post(BASE + "/" + id + "/background-reports/" + reportId + "/suggestions/adopt"),
                "{\"indexes\":[0]}"), token("it_bg_noedit")).getStatus(), "采纳需要「编辑客户」");
    }

    // ---------------------------------------------------------------- 导出 PDF（4.6）

    @Test
    void export_pdfContainsReportNumberAndAdoption() throws Exception {
        Assumptions.assumeTrue(converter.available(), "本机未安装 LibreOffice");
        long[] x = doneReport();
        ok(adopt(x[0], x[1], "[0]"));
        String reportNo = jdbc.queryForObject("select report_no from customer_background_report where id = ?", String.class, x[1]);

        MockHttpServletResponse res = perform(get(BASE + "/" + x[0] + "/background-reports/" + x[1] + "/export"), admin);
        assertEquals(200, res.getStatus(), res.getContentAsString(StandardCharsets.UTF_8));
        assertEquals("application/pdf", res.getContentType());
        assertTrue(res.getHeader("Content-Disposition").contains(
                java.net.URLEncoder.encode("Pacific Controls Pty Ltd_" + reportNo + ".pdf", StandardCharsets.UTF_8).replace("+", "%20")),
                res.getHeader("Content-Disposition"));
        byte[] body = res.getContentAsByteArray();
        assertEquals("%PDF", new String(body, 0, 4, StandardCharsets.US_ASCII), "导出文件是 PDF");
        try (var doc = Loader.loadPDF(body)) {
            String text = new PDFTextStripper().getText(doc);
            assertTrue(text.contains(reportNo), "包含报告编号：" + text);
            assertTrue(text.contains("Pacific Controls Pty Ltd"));
        }
    }

    @Test
    void export_requiresDoneReport() throws Exception {
        long id = customer();
        long reportId = ok(research(id, false)).path("id").asLong();
        JsonNode res = call(get(BASE + "/" + id + "/background-reports/" + reportId + "/export"), admin);
        assertEquals("报告还没有完成，不能导出", res.path("message").asText());
    }

    @Test
    void exportHtml_escapesRawHtmlAndRendersTables() {
        String html = BackgroundReportHtml.markdownToHtml("# 标题\n\n<script>alert(1)</script>\n\n| a | b |\n|---|---|\n| 1 | 2 |\n\n[x](javascript:alert(1))");
        assertFalse(html.contains("<script>"), html);
        assertTrue(html.contains("&lt;script&gt;"));
        assertTrue(html.contains("<table width"), "支持表格");
        assertFalse(html.contains("javascript:"), "链接只保留安全协议");
    }
}
