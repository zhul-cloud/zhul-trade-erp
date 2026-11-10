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
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 客户跟进快速操作（openspec change followup-quick-actions）：手动调整阶段、快速跟进（单个 / 批量）、团队视图快速记录次数。
 * 走完整 HTTP + JWT + 权限链路，连测试库执行真实 SQL；测试账号在平台租户（tenant 0），账号 ID 即 {@link #SELF}。
 */
@AutoConfigureMockMvc
class FollowupQuickActionsContractTest extends IntegrationTestBase {

    private static final String BASE = "/api/v1/followups";
    private static final long SELF = 99000002L;
    private static final int MENU = 110226;
    private static final int RES_RECORD = 110227;
    private static final int RES_OPPORTUNITY_ADD = 110161;
    private static final long C1 = 99600001L;
    private static final long C2 = 99600002L;
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
        for (String t : new String[]{"followup_item", "customer_followup", "customer_followup_item", "customer_field_source",
                "proforma_invoice_item", "proforma_invoice", "quotation_item", "quotation", "customer_inquiry",
                "opportunity_stage_log", "opportunity", "customer_contact", "customer_background_report", "sales_order"}) {
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

    private static void fails(JsonNode res, String message) {
        assertNotEquals(0, res.path("code").asInt(), res.toString());
        assertTrue(res.path("message").asText().contains(message), res.toString());
    }

    private void customer(long id) {
        jdbc.update("insert into customer (id, tenant_id, customer_code, name, country, owner_id, biz_grade, potential_rating)"
                + " values (?, 0, ?, ?, 'Germany', ?, 0, 3)", id, "CU" + id, "C" + id, SELF);
    }

    private void quote(long id, long customerId, LocalDateTime sentAt) {
        jdbc.update("insert into quotation (id, tenant_id, quotation_no, customer_id, owner_id, status, currency_code, total_amount,"
                + " total_amount_cny, sent_at, current_version_no) values (?, 0, ?, ?, ?, 2, 'USD', 100.00, 710.00, ?, 1)",
                id, "QT" + id, customerId, SELF, sentAt);
    }

    private void opportunity(long id, long customerId, String stage) {
        jdbc.update("insert into opportunity (id, tenant_id, opportunity_code, customer_id, owner_id, stage_code, first_contact_date,"
                + " reached_valid) values (?, 0, ?, ?, ?, ?, ?, 1)", id, "OPP" + id, customerId, SELF, stage, TODAY.minusDays(10));
    }

    private void sync(long customerId) {
        TenantContext.runAs(0, () -> followupSync.resync(customerId, TODAY, false));
    }

    private Map<String, Object> item(long customerId, int board) {
        return jdbc.queryForMap("select * from followup_item where tenant_id = 0 and customer_id = ? and board = ? and closed_at is null",
                customerId, board);
    }

    private long itemId(long customerId, int board) {
        return ((Number) item(customerId, board).get("id")).longValue();
    }

    private JsonNode adjust(String token, long itemId, String to, String reason, String note) throws Exception {
        return call(json(post(BASE + "/items/" + itemId + "/stage"), "{\"toStage\":\"" + to + "\",\"reason\":\"" + reason + "\""
                + (note == null ? "" : ",\"note\":\"" + note + "\"") + "}"), token);
    }

    private JsonNode quick(String token, long customerId, long itemId, String result, LocalDate contactDate) throws Exception {
        return call(json(post(BASE + "/quick"), "{\"customerId\":" + customerId + ",\"itemIds\":[" + itemId + "],\"result\":\"" + result
                + "\"" + (contactDate == null ? "" : ",\"contactDate\":\"" + contactDate + "\"")
                + ",\"idempotencyKey\":\"" + UUID.randomUUID().toString().substring(0, 30) + "\"}"), token);
    }

    // ---------------------------------------------------------------- 手动调整阶段

    @Test
    void adjust_oldCustomerQuote_withinSegment_survivesResync() throws Exception {
        customer(C1);
        quote(9601, C1, LocalDateTime.now().minusDays(1));
        sync(C1);
        long id = itemId(C1, 2);
        assertEquals("S4", item(C1, 2).get("stage_code"));
        String t = staff();

        assertEquals("S6", ok(adjust(t, id, "S6", "HISTORY", null)).asText());
        sync(C1);
        assertEquals("S6", item(C1, 2).get("stage_code"), "同步时保留在 S4–S6 之间");
        Map<String, Object> r = jdbc.queryForMap("select * from customer_followup where tenant_id = 0 and record_type = 3");
        assertEquals("调整阶段：S4 → S6", r.get("title"));
        assertEquals("历史数据阶段不对", r.get("ai_summary"));
        assertEquals("S4", r.get("stage_before"));
        assertEquals("S6", r.get("stage_after"));

        ok(adjust(t, id, "S5", "CHANGED", "客户说还在比价"));
        sync(C1);
        assertEquals("S5", item(C1, 2).get("stage_code"), "同段内可以后退");
        assertEquals("客户情况变化：客户说还在比价", jdbc.queryForObject(
                "select ai_summary from customer_followup where tenant_id = 0 and record_type = 3 order by id desc limit 1", String.class));

        fails(adjust(t, id, "S7", "HISTORY", null), "只能调整到 S4、S5、S6");
        fails(adjust(t, id, "S2", "HISTORY", null), "只能调整到");
        fails(adjust(t, id, "S6", "OTHER", null), "请写明调整原因");
        fails(adjust(t, id, "S6", "BAD", null), "调整原因不正确");
        fails(adjust(t, id, "S5", "HISTORY", null), "已经是 S5");
        assertEquals(0, jdbc.queryForObject("select count(*) from customer_followup where tenant_id = 0 and record_type = 1", Integer.class),
                "调整阶段不算一次跟进");
    }

    @Test
    void adjust_withOpportunity_movesOpportunity() throws Exception {
        customer(C1);
        opportunity(9701, C1, "S4");
        quote(9601, C1, LocalDateTime.now().minusDays(1));
        sync(C1);
        ok(adjust(staff(), itemId(C1, 2), "S5", "LAGGING", null));
        assertEquals("S5", jdbc.queryForObject("select stage_code from opportunity where id = 9701", String.class));
        assertEquals("手动调整：系统判断滞后", jdbc.queryForObject(
                "select note from opportunity_stage_log where opportunity_id = 9701 order by id desc limit 1", String.class));
        assertEquals("S5", item(C1, 2).get("stage_code"), "跟进对象随商机同步");
    }

    @Test
    void adjust_documentDrivenStage_isRejected() throws Exception {
        customer(C1);
        quote(9601, C1, LocalDateTime.now().minusDays(1));
        jdbc.update("insert into proforma_invoice (id, tenant_id, pi_no, customer_id, owner_id, status, receipt_status, currency_code,"
                + " total_amount, received_amount, total_amount_cny, sent_at, current_version_no)"
                + " values (9801, 0, 'FWPI9801', ?, ?, 2, 1, 'USD', 100, 0, 710, ?, 1)", C1, SELF, LocalDateTime.now().minusDays(1));
        jdbc.update("insert into proforma_invoice_item (tenant_id, pi_id, quotation_id, line_no, model, quantity) values (0, 9801, 9601, 1, 'M', 1)");
        sync(C1);
        assertEquals("S7", item(C1, 2).get("stage_code"));
        fails(adjust(staff(), itemId(C1, 2), "S6", "HISTORY", null), "由单据推进，不能手动调整");
    }

    @Test
    void adjust_develop_rewritesFacts_andRederives() throws Exception {
        String t = staff();
        long customerId = ok(call(json(post(BASE + "/develop"), "{\"name\":\"Quick Prospect\",\"country\":\"Germany\","
                + "\"reason\":\"展会名录\",\"contactName\":\"Anna\",\"email\":\"anna@example.com\"}"), t)).path("customerId").asLong();
        jdbc.update("update customer set potential_rating = 4 where id = ?", customerId);
        sync(customerId);
        long id = itemId(customerId, 1);
        assertEquals("D4", item(customerId, 1).get("stage_code"));

        ok(adjust(t, id, "D6", "HISTORY", null));
        sync(customerId);
        Map<String, Object> d = item(customerId, 1);
        assertEquals("D6", d.get("stage_code"));
        assertTrue(((Number) d.get("touch_count")).intValue() >= 1);

        ok(adjust(t, id, "D4", "OTHER", "其实还没联系过"));
        sync(customerId);
        d = item(customerId, 1);
        assertEquals("D4", d.get("stage_code"), "触达和回复清掉后重新推导仍是 D4");
        assertNull(d.get("replied_at"));
        assertNull(d.get("first_touch_date"));
    }

    // ---------------------------------------------------------------- 快速跟进

    @Test
    void quick_single_writesTaggedRecord_advances_andFillsNextStep() throws Exception {
        customer(C1);
        quote(9601, C1, LocalDateTime.now().minusDays(1));
        sync(C1);
        long id = itemId(C1, 2);
        String t = staff();

        JsonNode results = ok(call(get(BASE + "/quick-results"), t));
        assertEquals("FEEDBACK", results.path("S4").get(0).path("code").asText());
        assertEquals("S5", results.path("S4").get(0).path("advanceTo").asText());
        assertEquals("TOMORROW", results.path("S5").get(0).path("dateRule").asText());

        fails(quick(t, C1, id, "NEGOTIATING", null), "不能选「客户在谈条件」");
        fails(quick(t, C1, id, "FEEDBACK", TODAY.minusDays(31)), "实际沟通日期只能选今天和之前 30 天内");
        fails(quick(t, C1, id, "FEEDBACK", TODAY.plusDays(1)), "实际沟通日期");
        fails(quick(t, C1, id, "NOPE", null), "跟进结果不正确");

        assertEquals(1, ok(quick(t, C1, id, "FEEDBACK", TODAY.minusDays(2))).asInt());
        Map<String, Object> r = jdbc.queryForMap("select * from customer_followup where tenant_id = 0 and record_type = 1");
        assertEquals(1, ((Number) r.get("quick")).intValue());
        assertEquals(TODAY.minusDays(2), ((java.sql.Date) r.get("contact_date")).toLocalDate());
        assertEquals("客户给了反馈", r.get("ai_summary"));
        assertTrue(((java.sql.Date) r.get("next_action_date")).toLocalDate().isAfter(TODAY), "按推荐日期");
        assertTrue(!((String) r.get("next_action")).isBlank());
        assertEquals("S5", item(C1, 2).get("stage_code"));

        ok(quick(t, C1, id, "NEGOTIATING", null));
        Map<String, Object> last = jdbc.queryForMap(
                "select * from customer_followup where tenant_id = 0 and record_type = 1 order by id desc limit 1");
        assertEquals(TODAY.plusDays(1), ((java.sql.Date) last.get("next_action_date")).toLocalDate(), "在谈条件：明天");
        assertEquals(TODAY, ((java.sql.Date) last.get("contact_date")).toLocalDate(), "不填为今天");
        assertEquals("S6", item(C1, 2).get("stage_code"));

        ok(quick(t, C1, id, "NO_REPLY", null));
        last = jdbc.queryForMap("select * from customer_followup where tenant_id = 0 and record_type = 1 order by id desc limit 1");
        assertTrue(!((java.sql.Date) last.get("next_action_date")).toLocalDate().isAfter(TODAY.plusDays(3)), "没回复最晚 3 天后");
    }

    @Test
    void quick_s1Replied_advancesOpportunityToS2() throws Exception {
        customer(C1);
        opportunity(9701, C1, "S1");
        sync(C1);
        long id = itemId(C1, 2);
        assertEquals("S1", item(C1, 2).get("stage_code"));
        ok(quick(staff(), C1, id, "S1_REPLIED", null));
        assertEquals("S2", jdbc.queryForObject("select stage_code from opportunity where id = 9701", String.class));
        assertEquals("S2", item(C1, 2).get("stage_code"));
        assertEquals("S2", jdbc.queryForObject(
                "select stage_after from customer_followup where tenant_id = 0 and record_type = 1", String.class),
                "时间线显示 S1 → S2");
    }

    @Test
    void quickBatch_perCustomer_failureDoesNotBlockOthers() throws Exception {
        customer(C1);
        customer(C2);
        quote(9601, C1, LocalDateTime.now().minusDays(1));
        quote(9602, C2, LocalDateTime.now().minusDays(1));
        sync(C1);
        sync(C2);
        String key = UUID.randomUUID().toString().substring(0, 20);
        String body = "{\"contactDate\":\"" + TODAY.minusDays(1) + "\",\"idempotencyKey\":\"" + key + "\",\"entries\":["
                + "{\"customerId\":" + C1 + ",\"itemIds\":[" + itemId(C1, 2) + "],\"result\":\"FEEDBACK\"},"
                + "{\"customerId\":" + C2 + ",\"itemIds\":[" + itemId(C2, 2) + "],\"result\":\"PAYING\"}]}";
        String t = staff();
        JsonNode vo = ok(call(json(post(BASE + "/quick-batch"), body), t));
        assertEquals(1, vo.path("success").asInt());
        assertEquals(1, vo.path("failures").size());
        assertEquals(C2, vo.path("failures").get(0).path("customerId").asLong());
        assertEquals("S5", item(C1, 2).get("stage_code"));
        assertEquals("S4", item(C2, 2).get("stage_code"));

        ok(call(json(post(BASE + "/quick-batch"), body), t));
        assertEquals(1, jdbc.queryForObject("select count(*) from customer_followup where tenant_id = 0 and quick = 1", Integer.class),
                "重复提交不重复记");
    }

    @Test
    void team_countsQuickRecords_andIgnoresStageAdjustments() throws Exception {
        customer(C1);
        quote(9601, C1, LocalDateTime.now().minusDays(1));
        sync(C1);
        long id = itemId(C1, 2);
        String t = staff();
        ok(quick(t, C1, id, "FEEDBACK", null));
        ok(call(json(post(BASE + "/records"), "{\"customerId\":" + C1 + ",\"itemId\":" + id + ",\"situation\":0,"
                + "\"nextAction\":\"问报价反馈\",\"nextActionDate\":\"" + TODAY.plusDays(2) + "\",\"channel\":1,"
                + "\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}"), t));
        ok(adjust(t, id, "S6", "HISTORY", null));

        loginAsAdmin("fu_admin");
        JsonNode owners = ok(call(get(BASE + "/team"), token("fu_admin"))).path("owners");
        JsonNode me = null;
        for (JsonNode o : owners) {
            if (o.path("ownerId").asLong() == SELF) {
                me = o;
            }
        }
        assertTrue(me != null, owners.toString());
        assertEquals(2, me.path("records").asInt());
        assertEquals(1, me.path("quickRecords").asInt());
    }
}
