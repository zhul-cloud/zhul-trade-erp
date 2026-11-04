package com.zhul.erp.modules.crm.followup;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.common.constants.RedisKeyConstants;
import com.zhul.erp.common.utils.JwtUtils;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.crm.followup.support.FollowupSync;
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
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 主动开发的接口契约（第四期 tasks 3.1–3.7、2.3）：新建与查重、加入条件、触达结果与阶段、转商机、停止与重新开发、
 * 自动结束、列表与链路卡片、团队指标。测试账号在平台租户（tenant 0），账号 ID 即 {@link #SELF}。
 */
@AutoConfigureMockMvc
class DevelopApiContractTest extends IntegrationTestBase {

    private static final String BASE = "/api/v1/followups";
    private static final long SELF = 99000002L;
    private static final int MENU = 110226;
    private static final int RES_RECORD = 110227;
    private static final int RES_OPPORTUNITY_ADD = 110161;
    private static final long C1 = 99500001L;
    private static final long C2 = 99500002L;
    private static final LocalDate TODAY = LocalDate.now();

    @Autowired private MockMvc mvc;
    @Autowired private JwtUtils jwtUtils;
    @Autowired private StringRedisTemplate redis;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private FollowupSync followupSync;

    @BeforeEach
    void setUp() {
        cleanup();
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    private void cleanup() {
        for (String t : new String[]{"followup_item", "customer_followup", "customer_inquiry", "opportunity", "opportunity_stage_log",
                "customer_contact", "customer_background_report", "sales_order"}) {
            jdbc.update("delete from " + t + " where tenant_id = 0");
        }
        jdbc.update("delete from customer where tenant_id = 0");
    }

    // ---------------------------------------------------------------- 工具

    private String token(String username) {
        String token = jwtUtils.generateToken(Map.of("tenantId", 0), username, 3600);
        redis.opsForValue().set(RedisKeyConstants.TOKEN_PREFIX + token, "1");
        return token;
    }

    private String staff() {
        loginWithResources("fu_staff", MENU, RES_RECORD, RES_OPPORTUNITY_ADD);
        return token("fu_staff");
    }

    private JsonNode call(MockHttpServletRequestBuilder request, String token) throws Exception {
        request.header("Authorization", "Bearer " + token);
        return objectMapper.readTree(mvc.perform(request).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder b, String body) {
        return b.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static JsonNode ok(JsonNode res) {
        assertEquals(0, res.path("code").asInt(), res.toString());
        return res.path("data");
    }

    private static void fails(JsonNode res, String code) {
        assertEquals(code, res.path("data").path("errorCode").asText(), res.toString());
    }

    private void customer(long id, int bizGrade, int potential) {
        jdbc.update("insert into customer (id, tenant_id, customer_code, name, country, owner_id, biz_grade, potential_rating)"
                + " values (?, 0, ?, ?, 'Germany', ?, ?, ?)", id, "CU" + id, "C" + id, SELF, bizGrade, potential);
    }

    private void contact(long customerId, String email) {
        jdbc.update("insert into customer_contact (tenant_id, customer_id, is_primary, name, title, email) values (0, ?, 1, 'Hans', 'Buyer', ?)",
                customerId, email);
    }

    private void report(long customerId, int status, int priority) {
        jdbc.update("insert into customer_background_report (tenant_id, customer_id, status, priority, key_finding)"
                + " values (0, ?, ?, ?, '在用 ABB 变频器')", customerId, status, priority);
    }

    private void sync(long customerId) {
        TenantContext.runAs(0, () -> followupSync.resync(customerId, TODAY, false));
    }

    private Map<String, Object> develop(long customerId) {
        return jdbc.queryForMap("select * from followup_item where tenant_id = 0 and board = 1 and customer_id = ?", customerId);
    }

    private long developId(long customerId) {
        return ((Number) develop(customerId).get("id")).longValue();
    }

    private JsonNode join(String token, long customerId) throws Exception {
        return call(json(post(BASE + "/develop/" + customerId + "/join"), "{\"reason\":\"在用 ABB 变频器，有备件需求\",\"source\":2}"), token);
    }

    private JsonNode touch(String token, long customerId, int result, String extra) throws Exception {
        return call(json(post(BASE + "/records"), "{\"customerId\":" + customerId + ",\"itemId\":" + developId(customerId)
                + ",\"situation\":0,\"nextAction\":\"第 4 天换一个价值点\",\"nextActionDate\":\"" + TODAY.plusDays(3) + "\","
                + "\"channel\":1,\"touchResult\":" + result + ",\"idempotencyKey\":\"" + UUID.randomUUID() + "\"" + extra + "}"), token);
    }

    // ---------------------------------------------------------------- 新建与查重（tasks 3.1）

    @Test
    void create_newProspect_isD1WithoutContactRow() throws Exception {
        JsonNode card = ok(call(json(post(BASE + "/develop"), "{\"name\":\"Prospect GmbH\",\"country\":\"Germany\","
                + "\"reason\":\"展会名录里的系统集成商\",\"source\":3}"), staff()));
        assertEquals("open", card.path("state").asText());
        assertEquals("D1", card.path("stageCode").asText());
        assertEquals("展会名录", card.path("prospectSourceName").asText());
        long customerId = card.path("customerId").asLong();
        Map<String, Object> c = jdbc.queryForMap("select source_channel, owner_id from customer where id = ?", customerId);
        assertEquals(7, ((Number) c.get("source_channel")).intValue(), "客户来源为主动开发");
        assertEquals(0, jdbc.queryForObject("select count(*) from customer_contact where customer_id = ?", Integer.class, customerId),
                "没填联系人不建联系人");
    }

    @Test
    void create_withContact_isContactable() throws Exception {
        JsonNode card = ok(call(json(post(BASE + "/develop"), "{\"name\":\"Prospect Two\",\"country\":\"Germany\","
                + "\"reason\":\"LinkedIn 上找到的维修主管\",\"contactName\":\"Anna\",\"email\":\"anna@example.com\"}"), staff()));
        long customerId = card.path("customerId").asLong();
        jdbc.update("update customer set potential_rating = 4 where id = ?", customerId);
        sync(customerId);
        assertEquals("D4", develop(customerId).get("stage_code"), "合格且有联系方式");
    }

    @Test
    void create_duplicate_returnsExistingCustomer() throws Exception {
        String t = staff();
        String body = "{\"name\":\"Dup Prospect GmbH\",\"country\":\"Germany\",\"reason\":\"x\"}";
        long first = ok(call(json(post(BASE + "/develop"), body), t)).path("customerId").asLong();
        JsonNode res = call(json(post(BASE + "/develop"), body), t);
        fails(res, "CUSTOMER_DUPLICATE");
        assertEquals(first, res.path("data").path("detail").path("existingId").asLong(), res.toString());
        assertEquals(1, jdbc.queryForObject("select count(*) from followup_item where tenant_id = 0 and board = 1", Integer.class));
    }

    @Test
    void create_missingReason_isRejected() throws Exception {
        JsonNode res = call(json(post(BASE + "/develop"), "{\"name\":\"P\",\"country\":\"Germany\"}"), staff());
        assertFalse(res.path("code").asText().equals("0"), res.toString());
    }

    // ---------------------------------------------------------------- 加入条件（tasks 3.2）

    @Test
    void join_ineligibleCustomers_areRejected() throws Exception {
        String t = staff();
        customer(C1, 2, 0);
        fails(join(t, C1), "DEVELOP_NOT_ELIGIBLE");
        customer(C2, 0, 0);
        jdbc.update("insert into customer_inquiry (id, tenant_id, inquiry_code, customer_id, owner_id, status, source, inquiry_date,"
                + " quote_deadline) values (99520001, 0, 'INQ99520001', ?, ?, 1, 1, ?, ?)", C2, SELF, TODAY, TODAY.plusDays(3));
        sync(C2);
        fails(join(t, C2), "DEVELOP_NOT_ELIGIBLE");
        assertEquals("none", ok(call(get(BASE + "/develop/" + C2), t)).path("state").asText());
    }

    @Test
    void join_twice_isRejected() throws Exception {
        String t = staff();
        customer(C1, 0, 0);
        assertEquals("eligible", ok(call(get(BASE + "/develop/" + C1), t)).path("state").asText());
        ok(join(t, C1));
        fails(join(t, C1), "DEVELOP_NOT_ELIGIBLE");
        assertEquals(1, jdbc.queryForObject("select count(*) from customer_followup where tenant_id = 0 and record_type = 2"
                + " and title = '加入主动开发'", Integer.class));
    }

    // ---------------------------------------------------------------- 推导与触达（tasks 2.3、3.3）

    @Test
    void lifecycle_researchContactTouchReplySignal() throws Exception {
        String t = staff();
        customer(C1, 0, 0);
        ok(join(t, C1));
        assertEquals("D1", develop(C1).get("stage_code"));
        report(C1, 1, 0);
        sync(C1);
        assertEquals("D2", develop(C1).get("stage_code"), "背调中");
        assertNull(develop(C1).get("next_action_date"), "背调中没有提醒");
        jdbc.update("update customer_background_report set status = 2, priority = 1 where customer_id = ?", C1);
        sync(C1);
        assertEquals("D3", develop(C1).get("stage_code"), "合格但没有联系人");
        contact(C1, "hans@example.com");
        sync(C1);
        assertEquals("D4", develop(C1).get("stage_code"));

        ok(touch(t, C1, 1, ""));
        Map<String, Object> d = develop(C1);
        assertEquals("D5", d.get("stage_code"));
        assertEquals(1, ((Number) d.get("touch_count")).intValue());
        assertEquals(TODAY, ((java.sql.Date) d.get("first_touch_date")).toLocalDate());

        ok(touch(t, C1, 2, ""));
        d = develop(C1);
        assertEquals("D6", d.get("stage_code"));
        assertEquals(2, ((Number) d.get("touch_count")).intValue());
        assertNotNull(d.get("replied_at"));

        ok(touch(t, C1, 3, ",\"signalText\":\"ACS580 5 台，下月\""));
        d = develop(C1);
        assertEquals("D7", d.get("stage_code"));
        assertEquals("ACS580 5 台，下月", d.get("signal_text"));
        JsonNode card = ok(call(get(BASE + "/develop/" + C1), t));
        assertTrue(card.path("canConvert").asBoolean());
        assertEquals("在用 ABB 变频器", card.path("report").path("keyFinding").asText());
        assertEquals("Buyer", card.path("contacts").get(0).path("title").asText());
    }

    @Test
    void touch_invalidSubmissions_areRejected() throws Exception {
        String t = staff();
        customer(C1, 0, 3);
        ok(join(t, C1));
        long id = developId(C1);
        JsonNode noResult = call(json(post(BASE + "/records"), "{\"customerId\":" + C1 + ",\"itemId\":" + id + ",\"situation\":0,"
                + "\"nextAction\":\"x\",\"nextActionDate\":\"" + TODAY.plusDays(3) + "\",\"channel\":1,\"idempotencyKey\":\""
                + UUID.randomUUID() + "\"}"), t);
        assertFalse("0".equals(noResult.path("code").asText()), "触达结果必填：" + noResult);
        JsonNode signalOnSent = touch(t, C1, 1, ",\"signalText\":\"不该有\"");
        assertFalse("0".equals(signalOnSent.path("code").asText()), "只有采购信号能填信号内容：" + signalOnSent);
        assertEquals(0, ((Number) develop(C1).get("touch_count")).intValue(), "被拒的提交不改计数");
    }

    @Test
    void touch_noReplyAndRefused_keepStage() throws Exception {
        String t = staff();
        customer(C1, 0, 3);
        contact(C1, "hans@example.com");
        ok(join(t, C1));
        ok(touch(t, C1, 1, ""));
        ok(touch(t, C1, 4, ""));
        assertEquals("D5", develop(C1).get("stage_code"));
        assertEquals(1, ((Number) develop(C1).get("touch_count")).intValue(), "没回复不算一次触达");
        ok(touch(t, C1, 5, ""));
        assertEquals("D5", develop(C1).get("stage_code"), "明确拒绝由业务员随后停止开发");
    }

    // ---------------------------------------------------------------- 转商机（tasks 3.4）

    @Test
    void convert_createsOpportunityAndClosesDevelop() throws Exception {
        String t = staff();
        customer(C1, 0, 3);
        contact(C1, "hans@example.com");
        ok(join(t, C1));
        ok(touch(t, C1, 3, ",\"signalText\":\"ACS580 5 台\""));
        ok(call(json(post(BASE + "/items/" + developId(C1) + "/convert"), "{}"), t));
        Map<String, Object> d = develop(C1);
        assertNotNull(d.get("closed_at"));
        assertEquals(7, ((Number) d.get("close_reason")).intValue(), "结束原因为转商机，而不是已进入商机成交");
        Map<String, Object> o = jdbc.queryForMap("select source_channel, demand_summary, first_contact_date, opportunity_code"
                + " from opportunity where tenant_id = 0 and customer_id = ?", C1);
        assertEquals(7, ((Number) o.get("source_channel")).intValue());
        assertEquals("ACS580 5 台", o.get("demand_summary"));
        assertEquals(TODAY, ((java.sql.Date) o.get("first_contact_date")).toLocalDate());
        assertEquals(1, jdbc.queryForObject("select count(*) from followup_item where tenant_id = 0 and board = 2 and customer_id = ?"
                + " and closed_at is null", Integer.class, C1), "商机成交出现商机行");
        assertEquals(1, jdbc.queryForObject("select count(*) from customer_followup where tenant_id = 0 and record_type = 2 and title = ?",
                Integer.class, "转为商机 " + o.get("opportunity_code")));
    }

    @Test
    void convert_beforeD4_orWithoutPermission_isRejected() throws Exception {
        String t = staff();
        customer(C1, 0, 0);
        ok(join(t, C1));
        JsonNode early = call(json(post(BASE + "/items/" + developId(C1) + "/convert"), "{}"), t);
        assertFalse("0".equals(early.path("code").asText()), early.toString());
        assertNull(develop(C1).get("closed_at"));

        jdbc.update("update customer set potential_rating = 3 where id = ?", C1);
        contact(C1, "hans@example.com");
        sync(C1);
        loginWithResources("fu_noopp", MENU, RES_RECORD);
        JsonNode denied = call(json(post(BASE + "/items/" + developId(C1) + "/convert"), "{}"), token("fu_noopp"));
        assertFalse("0".equals(denied.path("code").asText()), "没有登记商机权限：" + denied);
        assertNull(develop(C1).get("closed_at"));
    }

    @Test
    void convert_withActiveOpportunity_isRejectedAndRollsBack() throws Exception {
        String t = staff();
        customer(C1, 0, 3);
        contact(C1, "hans@example.com");
        ok(join(t, C1));
        // 绕过推导直接插一条进行中的商机（正常流程下开发记录会随之结束）
        jdbc.update("insert into opportunity (tenant_id, opportunity_code, customer_id, owner_id, stage_code, source_channel,"
                + " first_contact_date) values (0, 'OPP99500001', ?, ?, 'S1', 1, ?)", C1, SELF, TODAY);
        JsonNode res = call(json(post(BASE + "/items/" + developId(C1) + "/convert"), "{}"), t);
        fails(res, "OPPORTUNITY_ALREADY_ACTIVE");
        assertEquals("OPP99500001", res.path("data").path("detail").path("opportunityCode").asText());
        assertNull(develop(C1).get("closed_at"), "整体回滚，开发记录不变");
    }

    // ---------------------------------------------------------------- 停止与重新开发（tasks 3.5、3.2）

    @Test
    void stop_otherNeedsNote_thenReopen() throws Exception {
        String t = staff();
        customer(C1, 0, 0);
        ok(join(t, C1));
        long id = developId(C1);
        JsonNode bad = call(json(post(BASE + "/items/" + id + "/stop"), "{\"reason\":\"OTHER\"}"), t);
        assertFalse("0".equals(bad.path("code").asText()), bad.toString());
        JsonNode unknown = call(json(post(BASE + "/items/" + id + "/stop"), "{\"reason\":\"NOPE\"}"), t);
        assertFalse("0".equals(unknown.path("code").asText()), unknown.toString());
        ok(call(json(post(BASE + "/items/" + id + "/stop"), "{\"reason\":\"REFUSED\",\"note\":\"已有固定供应商\"}"), t));
        Map<String, Object> d = develop(C1);
        assertEquals(8, ((Number) d.get("close_reason")).intValue());
        assertEquals("REFUSED", d.get("stop_reason"));
        assertEquals("stopped", ok(call(get(BASE + "/develop/" + C1), t)).path("state").asText());
        fails(join(t, C1), "DEVELOP_NOT_ELIGIBLE");

        ok(call(post(BASE + "/develop/" + C1 + "/reopen"), t));
        d = develop(C1);
        assertNull(d.get("closed_at"));
        assertEquals(id, ((Number) d.get("id")).longValue(), "重新打开同一条记录");
        assertEquals("在用 ABB 变频器，有备件需求", d.get("prospect_reason"));
        fails(call(post(BASE + "/develop/" + C1 + "/reopen"), t), "DEVELOP_NOT_ELIGIBLE");
    }

    // ---------------------------------------------------------------- 自动结束（tasks 2.3）

    @Test
    void customerInquiry_closesDevelopAsEnteredSales() throws Exception {
        String t = staff();
        customer(C1, 0, 0);
        ok(join(t, C1));
        jdbc.update("insert into customer_inquiry (id, tenant_id, inquiry_code, customer_id, owner_id, status, source, inquiry_date,"
                + " quote_deadline) values (99520002, 0, 'INQ99520002', ?, ?, 1, 1, ?, ?)", C1, SELF, TODAY, TODAY.plusDays(3));
        sync(C1);
        Map<String, Object> d = develop(C1);
        assertNotNull(d.get("closed_at"));
        assertEquals(9, ((Number) d.get("close_reason")).intValue());
        assertEquals("none", ok(call(get(BASE + "/develop/" + C1), t)).path("state").asText());
    }

    // ---------------------------------------------------------------- 列表与链路卡片（tasks 3.6）

    @Test
    void list_rankedByPotentialThenReportPriority() throws Exception {
        String t = staff();
        long[] ids = {99500011L, 99500012L, 99500013L, 99500014L};
        customer(ids[0], 0, 3);
        customer(ids[1], 0, 5);
        customer(ids[2], 0, 3);
        customer(ids[3], 0, 3);
        report(ids[0], 2, 3);
        report(ids[2], 2, 1);
        report(ids[3], 2, 0);
        for (long id : ids) {
            ok(join(t, id));
        }
        JsonNode list = ok(call(get(BASE + "/items").param("board", "1"), t));
        assertEquals(4, list.path("total").asInt());
        JsonNode rows = list.path("records");
        // 5 星最前；同为 3 星：优先级 1 → 未出结论 → 优先级 3
        assertEquals(ids[1], rows.get(0).path("customerId").asLong());
        assertEquals(ids[2], rows.get(1).path("customerId").asLong());
        assertEquals(ids[3], rows.get(2).path("customerId").asLong());
        assertEquals(ids[0], rows.get(3).path("customerId").asLong());
        assertEquals(1, rows.get(1).path("reportPriority").asInt());
        assertEquals(0, rows.get(2).path("reportPriority").asInt(0), "未出结论的报告不算优先级");
        assertEquals("在用 ABB 变频器，有备件需求", rows.get(0).path("prospectReason").asText());

        JsonNode overview = ok(call(get(BASE + "/overview"), t));
        JsonNode board = overview.path("boards").get(0);
        assertEquals(1, board.path("board").asInt());
        assertTrue(board.path("available").asBoolean());
        assertEquals(4, board.path("total").asInt());
        assertEquals(3, board.path("groups").size());
        assertEquals("找客户", board.path("groups").get(0).path("name").asText());
    }

    @Test
    void list_sequenceDayAndSuggestNurture() throws Exception {
        String t = staff();
        customer(C1, 0, 3);
        contact(C1, "hans@example.com");
        ok(join(t, C1));
        ok(touch(t, C1, 1, ""));
        jdbc.update("update followup_item set first_touch_date = ? where id = ?", TODAY.minusDays(8), developId(C1));
        JsonNode row = ok(call(get(BASE + "/items").param("board", "1"), t)).path("records").get(0);
        assertEquals(9, row.path("sequenceDay").asInt());
        assertFalse(row.path("suggestNurture").asBoolean());
        jdbc.update("update followup_item set first_touch_date = ? where id = ?", TODAY.minusDays(20), developId(C1));
        row = ok(call(get(BASE + "/items").param("board", "1"), t)).path("records").get(0);
        assertTrue(row.path("suggestNurture").asBoolean(), "序列发完仍没回复");
    }

    // ---------------------------------------------------------------- 团队指标（tasks 3.7）

    @Test
    void team_developMetrics() throws Exception {
        String t = staff();
        long[] ids = {99500021L, 99500022L, 99500023L, 99500024L};
        for (long id : ids) {
            customer(id, 0, 3);
            contact(id, "x" + id + "@example.com");
            ok(join(t, id));
        }
        ok(touch(t, ids[0], 1, ""));
        ok(touch(t, ids[1], 1, ""));
        ok(touch(t, ids[2], 2, ""));
        ok(touch(t, ids[2], 3, ",\"signalText\":\"5 台\""));
        ok(call(json(post(BASE + "/items/" + developId(ids[2]) + "/convert"), "{}"), t));
        ok(call(json(post(BASE + "/items/" + developId(ids[3]) + "/stop"), "{\"reason\":\"MISMATCH\"}"), t));
        loginAsAdmin("fu_admin");
        JsonNode team = ok(call(get(BASE + "/team").param("period", "week"), token("fu_admin")));
        JsonNode r = team.path("results");
        // 卡在哪一步：主动开发 D1–D7 排在最前，两个触达过的对象在 D5
        int developStages = 0;
        int d5 = -1;
        for (JsonNode s : team.path("stages")) {
            if (s.path("board").asInt() != 1) continue;
            developStages++;
            if ("D5".equals(s.path("stageCode").asText())) d5 = s.path("count").asInt();
        }
        assertEquals(1, team.path("stages").path(0).path("board").asInt());
        assertEquals(7, developStages);
        assertEquals(2, d5);
        assertEquals(4, r.path("developNew").asInt());
        assertEquals(3, r.path("developTouched").asInt());
        assertEquals(1, r.path("developReplied").asInt());
        assertEquals(33, r.path("developReplyPercent").asInt(), "1 ÷ 3 = 33%（HALF_UP）");
        assertEquals(1, r.path("developConverted").asInt());
        assertEquals(1, r.path("developStopped").asInt());
    }
}
