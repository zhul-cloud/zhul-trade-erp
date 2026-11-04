package com.zhul.erp.modules.crm.followup;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.common.constants.RedisKeyConstants;
import com.zhul.erp.common.utils.JwtUtils;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.crm.followup.service.FollowupReconcileJob;
import com.zhul.erp.modules.crm.followup.support.FollowupHooks;
import com.zhul.erp.modules.crm.followup.support.FollowupSync;
import com.zhul.erp.modules.sales.entity.ProformaInvoiceDO;
import com.zhul.erp.modules.sales.repository.ProformaInvoiceMapper;
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

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 客户跟进（openspec change customer-followup）：跟进对象同步、记一次跟进、延后 / 转培育、价值排序与可见范围、
 * 每晚核对、商机输单转培育与收款自动赢单。走完整 HTTP + JWT + 权限链路，连测试库执行真实 SQL。
 * 测试账号在平台租户（tenant 0），账号 ID 即 {@link #SELF}；非管理员没有配置数据权限，范围为「仅本人」。
 */
@AutoConfigureMockMvc
class FollowupApiContractTest extends IntegrationTestBase {

    private static final String BASE = "/api/v1/followups";
    private static final long SELF = 99000002L;
    private static final long OTHER = 99000031L;
    private static final int MENU = 110226;
    private static final int RES_RECORD = 110227;
    private static final long C1 = 99100001L;
    private static final long C2 = 99100002L;
    private static final long C3 = 99100003L;
    private static final long C4 = 99100004L;

    @Autowired private MockMvc mvc;
    @Autowired private JwtUtils jwtUtils;
    @Autowired private StringRedisTemplate redis;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private FollowupSync followupSync;
    @Autowired private FollowupHooks followupHooks;
    @Autowired private FollowupReconcileJob reconcileJob;
    @Autowired private ProformaInvoiceMapper piMapper;
    @Autowired private com.zhul.erp.modules.inquiry.customerinquiry.repository.CustomerInquiryMapper inquiryMapper;
    @Autowired private com.zhul.erp.modules.crm.service.OpportunityService opportunityService;
    @Autowired private com.zhul.erp.modules.sales.service.SalesOrderService salesOrderService;

    @BeforeEach
    void setUp() {
        cleanup();
        jdbc.update("insert into user_basic (id, tenant_id, name, username, role_code, status) values (?, 0, '李娜', 'it_fu_lina', '', 1)",
                OTHER);
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    private void cleanup() {
        for (String t : new String[]{"quotation_version", "after_sales", "logistics_shipment", "outbound_order_item", "outbound_order", "purchase_order_item", "purchase_order",
                "purchase_requirement", "sales_order_item",
                "sales_order", "sourcing_quote", "inquiry_item", "followup_item", "customer_followup", "customer_field_source", "proforma_invoice_item",
                "proforma_invoice", "quotation_item", "quotation", "customer_inquiry", "opportunity_stage_log", "opportunity"}) {
            jdbc.update("delete from " + t + " where tenant_id = 0");
        }
        jdbc.update("delete from customer where tenant_id = 0");
        jdbc.update("delete from user_basic where id = ?", OTHER);
    }

    // ---------------------------------------------------------------- 工具

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

    private static JsonNode ok(JsonNode res) {
        assertEquals(0, res.path("code").asInt(), res.toString());
        return res.path("data");
    }

    private void customer(long id, String name, int bizGrade, int potential) {
        jdbc.update("insert into customer (id, tenant_id, customer_code, name, country, owner_id, biz_grade, potential_rating, city, website)"
                + " values (?, 0, ?, ?, 'Germany', ?, ?, ?, '', 'https://old.example.com')", id, "CU" + id, name, SELF, bizGrade, potential);
    }

    private void quote(long id, long customerId, String usd, String cny, LocalDateTime sentAt, long owner) {
        jdbc.update("insert into quotation (id, tenant_id, quotation_no, customer_id, owner_id, status, currency_code, total_amount,"
                + " total_amount_cny, sent_at, current_version_no) values (?, 0, ?, ?, ?, 2, 'USD', ?, ?, ?, 1)",
                id, "QT" + id, customerId, owner, new BigDecimal(usd), new BigDecimal(cny), sentAt);
    }

    private void pi(long id, long customerId, long quotationId, String amount, String received, int receiptStatus) {
        jdbc.update("insert into proforma_invoice (id, tenant_id, pi_no, customer_id, owner_id, status, receipt_status, currency_code,"
                + " total_amount, received_amount, total_amount_cny, sent_at, current_version_no)"
                + " values (?, 0, ?, ?, ?, 2, ?, 'USD', ?, ?, ?, ?, 1)",
                id, "FWPI" + id, customerId, SELF, receiptStatus, new BigDecimal(amount), new BigDecimal(received),
                new BigDecimal(amount).multiply(BigDecimal.valueOf(7)), LocalDateTime.now().minusDays(1));
        jdbc.update("insert into proforma_invoice_item (tenant_id, pi_id, quotation_id, line_no, model, quantity) values (0, ?, ?, 1, 'M', 1)",
                id, quotationId);
    }

    private void opportunity(long id, long customerId, String stage) {
        jdbc.update("insert into opportunity (id, tenant_id, opportunity_code, customer_id, owner_id, stage_code, first_contact_date,"
                + " reached_valid) values (?, 0, ?, ?, ?, ?, ?, 1)", id, "OPP" + id, customerId, SELF, stage, LocalDate.now().minusDays(10));
    }

    private void sync(long customerId) {
        TenantContext.runAs(0, () -> followupSync.resync(customerId, LocalDate.now(), false));
    }

    private Map<String, Object> item(int targetType, long targetId) {
        return jdbc.queryForMap("select * from followup_item where tenant_id = 0 and target_type = ? and target_id = ?",
                targetType, targetId);
    }

    private String record(String token, long customerId, Long itemId, int situation, String extra) throws Exception {
        return call(json(post(BASE + "/records"), "{\"customerId\":" + customerId + (itemId == null ? "" : ",\"itemId\":" + itemId)
                + ",\"situation\":" + situation + ",\"nextAction\":\"发替代型号对比，问客户能否接受\",\"nextActionDate\":\""
                + LocalDate.now().plusDays(2) + "\",\"channel\":1,\"idempotencyKey\":\"" + UUID.randomUUID() + "\"" + extra + "}"),
                token).toString();
    }

    // ---------------------------------------------------------------- 跟进对象同步（tasks 5.1）

    @Test
    void quoteSent_thenPiReplaces_thenFullyPaidLeaves() {
        customer(C1, "ADORA", 0, 3);
        quote(9101, C1, "1980.00", "14000.00", LocalDateTime.now().minusDays(3), SELF);
        sync(C1);
        Map<String, Object> q = item(3, 9101);
        assertEquals("S4", q.get("stage_code"));
        assertEquals(2, ((Number) q.get("stage_group")).intValue());
        assertEquals(LocalDate.now(), ((java.sql.Date) q.get("next_action_date")).toLocalDate(), "第 3 天节点");
        assertTrue(((String) q.get("next_action")).startsWith("第 3 天："), (String) q.get("next_action"));
        assertEquals(0, new BigDecimal("14000.00").compareTo((BigDecimal) q.get("amount_cny")));

        jdbc.update("update followup_item set blocker_code = 'PRICE' where id = ?", q.get("id"));
        pi(9201, C1, 9101, "1980.00", "0", 1);
        sync(C1);
        Map<String, Object> closed = item(3, 9101);
        assertEquals(2, ((Number) closed.get("close_reason")).intValue(), "报价单由 PI 接替");
        Map<String, Object> p = item(4, 9201);
        assertNull(p.get("closed_at"));
        assertEquals("S7", p.get("stage_code"));
        assertEquals("PRICE", p.get("blocker_code"), "接替时继承卡点");

        jdbc.update("update proforma_invoice set receipt_status = 3, received_amount = 594 where id = 9201");
        sync(C1);
        assertNull(item(4, 9201).get("closed_at"), "部分到账仍在 PI 后");
        jdbc.update("update proforma_invoice set receipt_status = 4, received_amount = 1980 where id = 9201");
        sync(C1);
        assertEquals(1, ((Number) item(4, 9201).get("close_reason")).intValue(), "全额到账移出");
    }

    @Test
    void sameSourceInquiry_keepsLatestQuoteOnly() {
        customer(C1, "ADORA", 0, 3);
        quote(9101, C1, "100.00", "700.00", LocalDateTime.now().minusDays(5), SELF);
        quote(9102, C1, "120.00", "840.00", LocalDateTime.now().minusDays(1), SELF);
        for (long qid : new long[]{9101, 9102}) {
            jdbc.update("insert into quotation_item (tenant_id, quotation_id, customer_inquiry_id, is_current, line_no, model, quantity)"
                    + " values (0, ?, 777, 1, 1, 'M', 1)", qid);
        }
        sync(C1);
        assertEquals(1, jdbc.queryForObject("select count(*) from followup_item where tenant_id = 0 and closed_at is null", Integer.class));
        assertEquals(9102L, ((Number) jdbc.queryForObject(
                "select target_id from followup_item where tenant_id = 0 and closed_at is null", Long.class)).longValue());
    }

    @Test
    void syncFailure_doesNotBreakDocument() {
        // 客户不存在时同步什么也不做、不抛异常
        TenantContext.runAs(0, () -> followupSync.customerChanged(-1L));
        TenantContext.runAs(0, () -> followupSync.customerChanged(null));
    }

    // ---------------------------------------------------------------- 记一次跟进（tasks 6.5）

    @Test
    void record_oldCustomer_advancesItemOnly_profileFillsBlanks_idempotent() throws Exception {
        customer(C1, "ADORA", 0, 3);
        quote(9101, C1, "1980.00", "14000.00", LocalDateTime.now().minusDays(1), SELF);
        sync(C1);
        long itemId = ((Number) item(3, 9101).get("id")).longValue();
        loginWithResources("fu_staff", MENU, RES_RECORD);
        String staff = token("fu_staff");

        String key = UUID.randomUUID().toString();
        String body = "{\"customerId\":" + C1 + ",\"itemId\":" + itemId + ",\"situation\":1,\"blockerCode\":\"PRICE\","
                + "\"nextAction\":\"回复目标价\",\"nextActionDate\":\"" + LocalDate.now().plusDays(2) + "\",\"channel\":1,"
                + "\"rawContent\":\"customer: too expensive\",\"summary\":\"嫌贵\",\"idempotencyKey\":\"" + key + "\","
                + "\"profile\":{\"city\":\"Berlin\",\"website\":\"https://new.example.com\"}}";
        JsonNode first = ok(call(json(post(BASE + "/records"), body), staff));
        assertEquals("S4", first.path("stageBefore").asText());
        assertEquals("S5", first.path("stageAfter").asText());
        assertFalse(first.path("opportunityAdvanced").asBoolean(), "老客户没有商机");
        JsonNode again = ok(call(json(post(BASE + "/records"), body), staff));
        assertTrue(again.path("duplicate").asBoolean());
        assertEquals(first.path("recordId").asLong(), again.path("recordId").asLong());
        assertEquals(1, jdbc.queryForObject("select count(*) from customer_followup where tenant_id = 0 and record_type = 1", Integer.class));

        Map<String, Object> it = item(3, 9101);
        assertEquals("S5", it.get("stage_code"));
        assertEquals("PRICE", it.get("blocker_code"));
        assertEquals(2, ((Number) it.get("next_action_source")).intValue());
        assertEquals(LocalDate.now().plusDays(2), ((java.sql.Date) it.get("next_action_date")).toLocalDate(), "当前待办关闭");
        assertEquals(1, ((Number) it.get("feedback_seen")).intValue());
        assertEquals("Berlin", jdbc.queryForObject("select city from customer where id = ?", String.class, C1));
        assertEquals("https://old.example.com", jdbc.queryForObject("select website from customer where id = ?", String.class, C1),
                "已有值不覆盖");
        assertEquals(first.path("recordId").asLong(), jdbc.queryForObject(
                "select followup_id from customer_field_source where customer_id = ? and field_key = 'city' and source = 4",
                Long.class, C1));
        assertEquals(0, jdbc.queryForObject("select count(*) from opportunity_stage_log where tenant_id = 0", Integer.class));

        ok(objectMapper.readTree(record(staff, C1, itemId, 2, "")));
        assertEquals("S6", item(3, 9101).get("stage_code"));
        record(staff, C1, itemId, 1, "");
        assertEquals("S6", item(3, 9101).get("stage_code"), "只进不退");
        record(staff, C1, itemId, 4, "");
        assertEquals(1, ((Number) item(3, 9101).get("no_reply_streak")).intValue());
    }

    @Test
    void record_newCustomer_advancesOpportunity() throws Exception {
        customer(C1, "NEWCO", 0, 0);
        opportunity(9301, C1, "S4");
        quote(9101, C1, "500.00", "3500.00", LocalDateTime.now().minusDays(1), SELF);
        sync(C1);
        long itemId = ((Number) item(3, 9101).get("id")).longValue();
        loginWithResources("fu_staff", MENU, RES_RECORD);
        String staff = token("fu_staff");
        JsonNode res = objectMapper.readTree(record(staff, C1, itemId, 2, ""));
        assertTrue(ok(res).path("opportunityAdvanced").asBoolean(), res.toString());
        assertEquals("S6", jdbc.queryForObject("select stage_code from opportunity where id = 9301", String.class));
        assertEquals("记跟进，自动推进", jdbc.queryForObject(
                "select note from opportunity_stage_log where opportunity_id = 9301 order by id desc limit 1", String.class));
        assertEquals("S6", item(3, 9101).get("stage_code"), "跟进对象随商机同步");
    }

    @Test
    void record_failureRollsBackEverything() throws Exception {
        customer(C1, "ADORA", 0, 3);
        quote(9101, C1, "1980.00", "14000.00", LocalDateTime.now().minusDays(1), SELF);
        sync(C1);
        long itemId = ((Number) item(3, 9101).get("id")).longValue();
        loginWithResources("fu_staff", MENU, RES_RECORD);
        String staff = token("fu_staff");
        JsonNode res = objectMapper.readTree(record(staff, C1, itemId, 1, ",\"profile\":{\"city\":\"Berlin\",\"currency\":\"EUR\"}"));
        assertNotEquals(0, res.path("code").asInt());
        assertEquals(0, jdbc.queryForObject("select count(*) from customer_followup where tenant_id = 0", Integer.class));
        assertEquals("S4", item(3, 9101).get("stage_code"));
        assertEquals("", jdbc.queryForObject("select city from customer where id = ?", String.class, C1));

        JsonNode noNext = call(json(post(BASE + "/records"), "{\"customerId\":" + C1 + ",\"nextAction\":\"\",\"nextActionDate\":\""
                + LocalDate.now() + "\",\"idempotencyKey\":\"k1\"}"), staff);
        assertEquals("请写下一步要做什么", noNext.path("message").asText(), noNext.toString());
        loginWithResources("fu_viewer", MENU);
        assertEquals(403, perform(json(post(BASE + "/records"), "{\"customerId\":" + C1 + ",\"nextAction\":\"x\",\"nextActionDate\":\""
                + LocalDate.now() + "\",\"idempotencyKey\":\"k2\"}"), token("fu_viewer")).getStatus(), "没有记跟进权限");
    }

    // ---------------------------------------------------------------- 延后 / 转培育 / 激活（tasks 6.4）

    @Test
    void snooze_nurture_activate() throws Exception {
        customer(C1, "ADORA", 0, 3);
        quote(9101, C1, "1980.00", "14000.00", LocalDateTime.now().minusDays(1), SELF);
        sync(C1);
        long itemId = ((Number) item(3, 9101).get("id")).longValue();
        loginWithResources("fu_staff", MENU, RES_RECORD);
        String staff = token("fu_staff");

        ok(call(json(post(BASE + "/items/" + itemId + "/snooze"), "{\"date\":\"" + LocalDate.now().plusDays(1) + "\"}"), staff));
        Map<String, Object> it = item(3, 9101);
        assertEquals(1, ((Number) it.get("snooze_count")).intValue());
        assertEquals(3, ((Number) it.get("next_action_source")).intValue(), "延后的系统提醒日期固定");
        assertEquals(0, jdbc.queryForObject("select count(*) from customer_followup where tenant_id = 0", Integer.class), "延后不写跟进记录");
        assertEquals("请选择今天以后的日期", call(json(post(BASE + "/items/" + itemId + "/snooze"),
                "{\"date\":\"" + LocalDate.now() + "\"}"), staff).path("message").asText());

        JsonNode noReason = call(json(post(BASE + "/items/" + itemId + "/nurture"), "{\"until\":\"" + LocalDate.now().plusDays(60) + "\"}"), staff);
        assertNotEquals(0, noReason.path("code").asInt(), "没有原因不能转培育");
        ok(call(json(post(BASE + "/items/" + itemId + "/nurture"), "{\"reason\":\"NO_RESPONSE\",\"until\":\""
                + LocalDate.now().plusDays(60) + "\"}"), staff));
        assertEquals(0, ok(call(get(BASE + "/items").param("group", "2"), staff)).path("total").asInt(), "培育中不占原阶段列表");
        assertEquals(1, ok(call(get(BASE + "/items").param("nurture", "true"), staff)).path("total").asInt());
        assertEquals(2, jdbc.queryForObject("select status from quotation where id = 9101", Integer.class), "转培育不改报价单状态");

        ok(call(post(BASE + "/items/" + itemId + "/activate"), staff));
        it = item(3, 9101);
        assertNull(it.get("nurture_until"));
        assertEquals(LocalDate.now(), ((java.sql.Date) it.get("next_action_date")).toLocalDate());
        assertEquals(1, ok(call(get(BASE + "/today"), staff)).path("today").size());

        jdbc.update("update quotation set status = 3 where id = 9101");
        sync(C1);
        assertEquals("这条跟进已经结束，请刷新后重试", call(json(post(BASE + "/items/" + itemId + "/snooze"),
                "{\"date\":\"" + LocalDate.now().plusDays(1) + "\"}"), staff).path("message").asText(), "已结束的不能延后");
    }

    @Test
    void nurtureExpiry_reactivatesOnReconcile() {
        customer(C1, "ADORA", 0, 3);
        quote(9101, C1, "1980.00", "14000.00", LocalDateTime.now().minusDays(20), SELF);
        sync(C1);
        jdbc.update("update followup_item set nurture_until = ?, nurture_reason = 'PRICE', next_action_date = null where target_id = 9101",
                LocalDate.now());
        TenantContext.runAs(0, () -> reconcileJob.reconcile(0));
        Map<String, Object> it = item(3, 9101);
        assertNull(it.get("nurture_until"));
        assertEquals("培育到期，重新联系客户", it.get("next_action"));
        assertEquals(3, ((Number) it.get("next_action_source")).intValue());
    }

    // ---------------------------------------------------------------- 排序、今日待办、可见范围（tasks 6.1–6.3）

    @Test
    void list_sortedByCustomerValue_andScopedToOwner() throws Exception {
        customer(C1, "C-GRADE", 4, 1);
        customer(C2, "FIVE-STAR", 0, 5);
        customer(C3, "THREE-BIG", 0, 3);
        customer(C4, "THREE-SMALL", 0, 3);
        LocalDateTime sent = LocalDateTime.now().minusDays(1);
        quote(9101, C1, "1980.00", "14000.00", sent, SELF);
        quote(9102, C2, "8500.00", "60000.00", sent, SELF);
        quote(9103, C3, "3260.00", "23000.00", sent, SELF);
        quote(9104, C4, "640.00", "4500.00", sent, SELF);
        quote(9105, C4, "999.00", "7000.00", sent, OTHER);
        for (long c : new long[]{C1, C2, C3, C4}) {
            sync(c);
        }
        loginWithResources("fu_staff", MENU, RES_RECORD);
        String staff = token("fu_staff");
        JsonNode list = ok(call(get(BASE + "/items").param("group", "2"), staff));
        assertEquals(4, list.path("total").asInt(), "业务员只看自己的");
        assertEquals("C-GRADE", list.path("records").get(0).path("customerName").asText(), "有等级的排在前");
        assertEquals("C", list.path("records").get(0).path("bizGrade").asText());
        assertEquals("FIVE-STAR", list.path("records").get(1).path("customerName").asText());
        assertEquals("THREE-BIG", list.path("records").get(2).path("customerName").asText(), "同潜力按金额档");
        assertEquals("THREE-SMALL", list.path("records").get(3).path("customerName").asText());
        assertEquals(4, ok(call(get(BASE + "/items").param("group", "2").param("ownerScope", "all"), staff)).path("total").asInt(),
                "没有查看团队权限时只能看自己");

        JsonNode overview = ok(call(get(BASE + "/overview"), staff));
        JsonNode sales = overview.path("boards").get(1);
        assertTrue(sales.path("available").asBoolean());
        assertFalse(overview.path("boards").get(0).path("available").asBoolean(), "未上线的板块");
        assertEquals(4, sales.path("customers").asInt());
        assertEquals(4, sales.path("groups").get(1).path("count").asInt());

        jdbc.update("update followup_item set next_action_date = ? where tenant_id = 0", LocalDate.now().plusDays(3));
        jdbc.update("update followup_item set next_action_date = ?, snooze_count = 3 where target_id = 9104", LocalDate.now().minusDays(2));
        jdbc.update("update followup_item set next_action_date = ? where target_id = 9101", LocalDate.now());
        JsonNode today = ok(call(get(BASE + "/today"), staff));
        assertEquals(1, today.path("overdue").size());
        assertEquals(2, today.path("overdue").get(0).path("overdueDays").asInt());
        assertFalse(today.path("overdue").get(0).path("manySnoozes").asBoolean(), "只有主管查看团队时标出");
        assertEquals(1, today.path("today").size());

        loginAsAdmin("fu_admin");
        String admin = token("fu_admin");
        JsonNode all = ok(call(get(BASE + "/items").param("group", "2").param("ownerScope", "all"), admin));
        assertEquals(5, all.path("total").asInt());
        assertEquals(1, ok(call(get(BASE + "/items").param("ownerScope", String.valueOf(OTHER)), admin)).path("total").asInt());
        assertTrue(ok(call(get(BASE + "/today").param("ownerScope", "all"), admin)).path("overdue").get(0)
                .path("manySnoozes").asBoolean(), "主管查看团队时标多次延后");
    }

    @Test
    void list_filtersByKeywordAndBlocker() throws Exception {
        customer(C1, "ALPHA TRADING", 0, 3);
        customer(C2, "BETA SUPPLY", 0, 3);
        LocalDateTime sent = LocalDateTime.now().minusDays(1);
        quote(9111, C1, "100.00", "700.00", sent, SELF);
        quote(9112, C2, "200.00", "1400.00", sent, SELF);
        sync(C1);
        sync(C2);
        jdbc.update("update followup_item set blocker_code = 'PRICE' where target_id = 9112");
        loginWithResources("fu_staff", MENU, RES_RECORD);
        String staff = token("fu_staff");
        JsonNode byName = ok(call(get(BASE + "/items").param("keyword", "alpha"), staff));
        assertEquals(1, byName.path("total").asInt(), "按客户名称");
        assertEquals("ALPHA TRADING", byName.path("records").get(0).path("customerName").asText());
        assertEquals(1, ok(call(get(BASE + "/items").param("keyword", "QT9112"), staff)).path("total").asInt(), "按单号");
        assertEquals(1, ok(call(get(BASE + "/items").param("keyword", "CU" + C1), staff)).path("total").asInt(), "按客户编码");
        assertEquals(0, ok(call(get(BASE + "/items").param("keyword", "%"), staff)).path("total").asInt(),
                "通配符按普通字符查");
        JsonNode byBlocker = ok(call(get(BASE + "/items").param("blocker", "PRICE"), staff));
        assertEquals(1, byBlocker.path("total").asInt());
        assertEquals("BETA SUPPLY", byBlocker.path("records").get(0).path("customerName").asText());
    }

    @Test
    void pi_list_unpaidByCurrency() throws Exception {
        customer(C1, "ADORA", 0, 3);
        quote(9101, C1, "12400.00", "88000.00", LocalDateTime.now().minusDays(3), SELF);
        pi(9201, C1, 9101, "12400.00", "3720.00", 3);
        sync(C1);
        loginWithResources("fu_staff", MENU);
        JsonNode list = ok(call(get(BASE + "/items").param("group", "3"), token("fu_staff")));
        assertEquals(1, list.path("total").asInt());
        assertEquals(0, new BigDecimal("8680.00").compareTo(list.path("records").get(0).path("unpaidAmount").decimalValue()));
        assertEquals("USD", list.path("unpaid").get(0).path("currencyCode").asText());
        assertEquals(0, new BigDecimal("8680.00").compareTo(list.path("unpaid").get(0).path("amount").decimalValue()));
    }

    // ---------------------------------------------------------------- 作战卡与客户时间线（tasks 6.6、2.3）

    @Test
    void sopCard_andCustomerTimeline() throws Exception {
        loginWithResources("fu_staff", MENU, RES_RECORD);
        String staff = token("fu_staff");
        JsonNode card = ok(call(get(BASE + "/sop/2/S5"), staff));
        assertEquals("S5", card.path("stageCode").asText());
        assertFalse(card.path("goal").asText().isEmpty());
        assertFalse(card.path("checklist").isEmpty());
        assertEquals(10, card.path("blockers").size());

        customer(C1, "ADORA", 0, 3);
        quote(9101, C1, "1980.00", "14000.00", LocalDateTime.now().minusDays(1), SELF);
        TenantContext.runAs(0, () -> followupSync.event(C1, 3, 9101L, "发送报价单 QT9101 · USD 1980.00"));
        record(staff, C1, null, 4, "");
        JsonNode summary = ok(call(get(BASE + "/customers/" + C1 + "/summary"), staff));
        assertEquals(2, summary.path("total").asInt());
        assertEquals(1, summary.path("records").get(0).path("recordType").asInt());
        assertEquals("IT", summary.path("records").get(0).path("operatorName").asText());
        assertEquals("系统", summary.path("records").get(1).path("operatorName").asText());
        assertEquals("S4", summary.path("current").path("stageCode").asText());
        assertEquals(1, summary.path("recent30").asInt());
        assertEquals(1, ok(call(get(BASE + "/customers/" + C1 + "/records").param("type", "system"), staff)).path("total").asInt());

        jdbc.update("update customer set owner_id = ? where id = ?", OTHER, C1);
        assertNotEquals(0, call(get(BASE + "/customers/" + C1 + "/records"), staff).path("code").asInt(), "跟进记录跟随客户可见范围");
    }

    // ---------------------------------------------------------------- 每晚核对（tasks 5.4）

    @Test
    void reconcile_fillsMissingItems_cadenceFromToday() {
        customer(C1, "ADORA", 0, 3);
        quote(9101, C1, "1980.00", "14000.00", LocalDateTime.now().minusDays(30), SELF);
        TenantContext.runAs(0, () -> reconcileJob.reconcile(0));
        Map<String, Object> it = item(3, 9101);
        assertEquals(LocalDate.now(), ((java.sql.Date) it.get("cadence_from")).toLocalDate(), "补生成的从今天起算");
        assertEquals(LocalDate.now().plusDays(1), ((java.sql.Date) it.get("next_action_date")).toLocalDate());
    }

    // ---------------------------------------------------------------- 商机：输单转培育、收款自动赢单（tasks 4.1、4.3）

    @Test
    void opportunityLost_nurtureOrClose() throws Exception {
        loginAsAdmin("fu_admin");
        String admin = token("fu_admin");
        customer(C1, "NEWCO", 0, 0);
        opportunity(9301, C1, "S5");
        quote(9101, C1, "500.00", "3500.00", LocalDateTime.now().minusDays(1), SELF);
        sync(C1);
        ok(call(json(post("/api/v1/crm/opportunities/9301/close"), "{\"result\":\"LOST\",\"reason\":11,\"nurture\":true}"), admin));
        Map<String, Object> it = item(3, 9101);
        assertNull(it.get("closed_at"));
        assertEquals(LocalDate.now().plusDays(60), ((java.sql.Date) it.get("nurture_until")).toLocalDate());
        assertEquals("PRICE", it.get("nurture_reason"), "输单原因对应的卡点");

        customer(C2, "NEWCO2", 0, 0);
        opportunity(9302, C2, "S5");
        quote(9102, C2, "500.00", "3500.00", LocalDateTime.now().minusDays(1), SELF);
        sync(C2);
        ok(call(json(post("/api/v1/crm/opportunities/9302/close"), "{\"result\":\"LOST\",\"reason\":11,\"nurture\":false}"), admin));
        assertEquals(3, ((Number) item(3, 9102).get("close_reason")).intValue());
        sync(C2);
        assertEquals(3, ((Number) item(3, 9102).get("close_reason")).intValue(), "不转培育的不会自动回到板块");
    }

    @Test
    void receiptConfirmed_autoWins() {
        loginAsAdmin("fu_admin");
        customer(C1, "NEWCO", 0, 0);
        opportunity(9301, C1, "S5");
        quote(9101, C1, "500.00", "3500.00", LocalDateTime.now().minusDays(2), SELF);
        pi(9201, C1, 9101, "500.00", "150.00", 3);
        TenantContext.runAs(0, () -> {
            ProformaInvoiceDO p = piMapper.selectById(9201L);
            followupHooks.receiptConfirmed(p, new BigDecimal("150.00"));
        });
        assertEquals("WON", jdbc.queryForObject("select stage_code from opportunity where id = 9301", String.class), "定金到账、S5 直接赢单");
        assertEquals(1, jdbc.queryForObject("select count(*) from opportunity_stage_log where opportunity_id = 9301 and to_stage = 'S7'",
                Integer.class), "先补一条推进到 S7");
        assertEquals(1, jdbc.queryForObject("select count(*) from customer_followup where customer_id = ? and title like '%自动赢单%'",
                Integer.class, C1), "时间线有自动赢单");

        customer(C2, "LOSTCO", 0, 0);
        opportunity(9302, C2, "LOST");
        pi(9202, C2, 0, "500.00", "150.00", 3);
        TenantContext.runAs(0, () -> followupHooks.receiptConfirmed(piMapper.selectById(9202L), new BigDecimal("150.00")));
        assertEquals("LOST", jdbc.queryForObject("select stage_code from opportunity where id = 9302", String.class), "已输单不处理");
    }

    @Test
    void piSent_advancesToS7_forwardOnly_closedIgnored() {
        loginAsAdmin("fu_admin");
        customer(C1, "NEWCO", 0, 0);
        opportunity(9301, C1, "S6");
        pi(9201, C1, 0, "500.00", "0", 1);
        TenantContext.runAs(0, () -> followupHooks.piSent(piMapper.selectById(9201L), true));
        assertEquals("S7", jdbc.queryForObject("select stage_code from opportunity where id = 9301", String.class), "S6→S7");
        assertEquals("发送 PI FWPI9201，自动推进", jdbc.queryForObject(
                "select note from opportunity_stage_log where opportunity_id = 9301 order by id desc limit 1", String.class));

        customer(C2, "AHEAD", 0, 0);
        opportunity(9302, C2, "S6");
        TenantContext.runAs(0, () -> opportunityService.autoAdvance(C2, "S5", "S4", "记跟进，自动推进"));
        assertEquals("S6", jdbc.queryForObject("select stage_code from opportunity where id = 9302", String.class), "S6 不回退到 S5");
        customer(C3, "EARLY", 0, 0);
        opportunity(9303, C3, "S2");
        TenantContext.runAs(0, () -> opportunityService.autoAdvance(C3, "S5", "S4", "记跟进，自动推进"));
        assertEquals("S2", jdbc.queryForObject("select stage_code from opportunity where id = 9303", String.class), "S4 之前不推进");
        customer(C4, "LOSTCO", 0, 0);
        opportunity(9304, C4, "LOST");
        pi(9204, C4, 0, "500.00", "0", 1);
        TenantContext.runAs(0, () -> followupHooks.piSent(piMapper.selectById(9204L), true));
        assertEquals("LOST", jdbc.queryForObject("select stage_code from opportunity where id = 9304", String.class), "已结束不处理");
        assertEquals(0, jdbc.queryForObject("select count(*) from opportunity_stage_log where opportunity_id in (9302, 9303, 9304)",
                Integer.class));
    }

    // ---------------------------------------------------------------- 询盘节点与询盘预估价值（tasks 5.1、5.2）

    private void inquiry(long id, long customerId, Long opportunityId) {
        jdbc.update("insert into customer_inquiry (id, tenant_id, inquiry_code, customer_id, owner_id, status, source, inquiry_date,"
                + " quote_deadline, opportunity_id) values (?, 0, ?, ?, ?, 1, 1, ?, ?, ?)", id, "INQ" + id, customerId, SELF,
                LocalDate.now(), LocalDate.now().plusDays(3), opportunityId);
    }

    private void inquiryItem(long id, long inquiryId, int qty, String... cnyPrices) {
        jdbc.update("insert into inquiry_item (id, tenant_id, customer_inquiry_id, line_no, original_model, quantity) values (?, 0, ?, 1, 'M', ?)",
                id, inquiryId, qty);
        for (String p : cnyPrices) {
            // 采购报价按各自币种录入，unit_price_cny 已折成本位币
            jdbc.update("insert into sourcing_quote (tenant_id, customer_inquiry_id, inquiry_item_id, status, no_stock, currency_code,"
                    + " unit_price, unit_price_cny) values (0, ?, ?, 2, 0, 'USD', 1, ?)", inquiryId, id, new BigDecimal(p));
        }
    }

    @Test
    void inquiryCreated_thenQuoteReplaces_thenQuoteDoneLeaves() {
        customer(C1, "ADORA", 0, 3);
        inquiry(9401, C1, null);
        inquiryItem(94011, 9401, 10, "700.00", "650.50");
        inquiryItem(94012, 9401, 3);
        inquiry(9402, C1, 9301L);
        TenantContext.runAs(0, () -> followupHooks.inquiryCreated(inquiryOf(9401)));
        Map<String, Object> inq = item(2, 9401);
        assertEquals("S3", inq.get("stage_code"));
        assertEquals(LocalDate.now(), ((java.sql.Date) inq.get("next_action_date")).toLocalDate(), "新询盘当天提醒首次回复");
        assertEquals(0, new BigDecimal("6505.00").compareTo((BigDecimal) inq.get("amount_cny")), "数量 × 最低单价，没有采购报价的明细不计");
        assertEquals(0, jdbc.queryForObject("select count(*) from followup_item where target_type = 2 and target_id = 9402", Integer.class),
                "从商机创建的询盘不单独跟进");
        assertEquals(1, jdbc.queryForObject("select count(*) from customer_followup where customer_id = ? and title = '创建询盘 INQ9401'",
                Integer.class, C1));

        jdbc.update("update followup_item set blocker_code = 'DELIVERY' where target_id = 9401");
        quote(9101, C1, "1980.00", "14000.00", LocalDateTime.now(), SELF);
        jdbc.update("insert into quotation_item (tenant_id, quotation_id, customer_inquiry_id, is_current, line_no, model, quantity)"
                + " values (0, 9101, 9401, 1, 1, 'M', 1)");
        jdbc.update("update customer_inquiry set status = 7 where id = 9401");
        sync(C1);
        assertEquals(2, ((Number) item(2, 9401).get("close_reason")).intValue(), "询盘由报价单接替");
        assertEquals("DELIVERY", item(3, 9101).get("blocker_code"));

        for (int status : new int[]{3, 4, 5}) {
            jdbc.update("update quotation set status = ? where id = 9101", status);
            sync(C1);
            assertEquals(1, ((Number) item(3, 9101).get("close_reason")).intValue(), "成交、未成交、作废都移出：" + status);
            jdbc.update("update quotation set status = 6 where id = 9101");
            sync(C1);
            assertNull(item(3, 9101).get("closed_at"), "部分成交仍在报价后");
        }
    }

    @Test
    void inquiryEstimate_zeroWithoutSourcingQuotes() {
        customer(C1, "ADORA", 0, 3);
        inquiry(9401, C1, null);
        inquiryItem(94011, 9401, 5);
        sync(C1);
        assertEquals(0, BigDecimal.ZERO.compareTo((BigDecimal) item(2, 9401).get("amount_cny")));
        assertEquals("CNY", item(2, 9401).get("currency_code"));
    }

    private com.zhul.erp.modules.inquiry.customerinquiry.entity.CustomerInquiryDO inquiryOf(long id) {
        return inquiryMapper.selectById(id);
    }

    // ---------------------------------------------------------------- 订单履约、售后评价的记一次跟进（第二期 tasks 5.1–5.3）

    private static final long SO1 = 99310001L;

    /** 一张已出运的订单：shippedDaysAgo 天前 DHL 出运（5 天到），型号进度为 progress */
    private void order(long soId, String progress, int shippedDaysAgo) {
        jdbc.update("insert into sales_order (id, tenant_id, so_no, customer_id, owner_id, currency_code, total_amount, total_amount_cny,"
                + " status, progress_code) values (?, 0, ?, ?, ?, 'USD', 1000, 7100, 1, ?)", soId, "SO" + soId, C1, SELF, progress);
        jdbc.update("insert into sales_order_item (id, tenant_id, so_id, model, quantity, progress_code) values (?, 0, ?, 'M1', 1, ?)",
                soId + 1000, soId, progress);
        jdbc.update("insert into purchase_requirement (tenant_id, so_id, so_item_id, model, quantity, status) values (0, ?, ?, 'M1', 1, 1)",
                soId, soId + 1000);
        if (shippedDaysAgo >= 0) {
            jdbc.update("insert into logistics_shipment (id, tenant_id, sh_no, customer_id, status, carrier, waybill_no, shipped_date)"
                    + " values (?, 0, ?, ?, 2, 'DHL', '1234567890', ?)", soId + 2000, "SH" + soId, C1, LocalDate.now().minusDays(shippedDaysAgo));
            jdbc.update("insert into outbound_order (id, tenant_id, ob_no, so_id, customer_id, status, logistics_id) values (?, 0, ?, ?, ?, 3, ?)",
                    soId + 3000, "OB" + soId, soId, C1, soId + 2000);
        }
    }

    private Map<String, Object> orderItem(int board) {
        return jdbc.queryForMap("select * from followup_item where tenant_id = 0 and board = ? and target_id = ?", board, SO1);
    }

    private JsonNode stageRecord(String token, int board, String extra) throws Exception {
        long itemId = ((Number) orderItem(board).get("id")).longValue();
        return objectMapper.readTree(record(token, C1, itemId, 0, extra));
    }

    private static String fail(JsonNode res) {
        assertNotEquals(0, res.path("code").asInt(), res.toString());
        return res.path("message").asText();
    }

    @Test
    void fulfilRecord_noticeAndEta() throws Exception {
        loginWithResources("fu_staff", MENU, RES_RECORD);
        String staff = token("fu_staff");
        customer(C1, "Müller GmbH", 2, 3);
        order(SO1, "SHIPPED", 0);
        sync(C1);
        assertEquals("F6", orderItem(3).get("stage_code"));

        // 字段与阶段不匹配
        JsonNode mismatch = stageRecord(staff, 3, ",\"receiptAnswer\":1");
        assertEquals("跟进对象已到其他阶段，请刷新后重试", fail(mismatch));
        assertEquals("FOLLOWUP_STAGE_MISMATCH", mismatch.path("data").path("errorCode").asText());
        assertEquals("沟通内容不正确", fail(stageRecord(staff, 3, ",\"noticeMask\":16")));

        // 运单照片 + 没回复；改预计送达到 3 天后：阶段不变
        ok(stageRecord(staff, 3, ",\"noticeMask\":9,\"etaDate\":\"" + LocalDate.now().plusDays(3) + "\""));
        Map<String, Object> f = orderItem(3);
        assertEquals("F6", f.get("stage_code"));
        assertEquals(2, ((Number) f.get("eta_source")).intValue());
        assertEquals(java.sql.Date.valueOf(LocalDate.now().plusDays(3)), f.get("eta_date"));
        assertEquals(1, ((Number) f.get("no_reply_streak")).intValue());
        assertEquals(9, jdbc.queryForObject("select notice_mask from customer_followup where tenant_id = 0 and record_type = 1"
                + " order by id desc limit 1", Integer.class));

        // 改到今天：按新日期转入售后评价
        JsonNode moved = ok(stageRecord(staff, 3, ",\"etaDate\":\"" + LocalDate.now() + "\""));
        assertEquals("F6", moved.path("stageBefore").asText());
        assertEquals("A1", moved.path("stageAfter").asText(), "转入售后评价，阶段记为新的售后行");
        assertFalse(moved.path("ended").asBoolean(), "转入售后评价不是售后结束");
        assertNotNull(orderItem(3).get("closed_at"));
        assertEquals("A1", orderItem(4).get("stage_code"));
    }

    @Test
    void afterSalesRecord_eachStage() throws Exception {
        loginWithResources("fu_staff", MENU, RES_RECORD);
        String staff = token("fu_staff");
        customer(C1, "Müller GmbH", 2, 3);
        order(SO1, "SHIPPED", 6);
        sync(C1);
        assertEquals("A1", orderItem(4).get("stage_code"));

        // A1 校验
        assertEquals("还没收到时请填新的预计送达日", fail(stageRecord(staff, 4, ",\"receiptAnswer\":3")));
        assertEquals("新的预计送达日不能早于今天", fail(stageRecord(staff, 4, ",\"receiptAnswer\":3,\"etaDate\":\""
                + LocalDate.now().minusDays(1) + "\"")));
        assertEquals("请选择问题类型", fail(stageRecord(staff, 4, ",\"receiptAnswer\":2")));
        assertEquals("问题类型不正确", fail(stageRecord(staff, 4, ",\"receiptAnswer\":2,\"issueTypes\":[\"受潮\"]")));
        assertEquals("FOLLOWUP_STAGE_MISMATCH", stageRecord(staff, 4, ",\"satisfaction\":1").path("data").path("errorCode").asText());

        // 还没收到：阶段不变，下一步日期为新的预计送达
        JsonNode notYet = ok(stageRecord(staff, 4, ",\"receiptAnswer\":3,\"etaDate\":\"" + LocalDate.now().plusDays(4) + "\""));
        assertEquals("A1", notYet.path("stageAfter").asText());
        assertTrue(notYet.path("orderCompleted").isMissingNode() || notYet.path("orderCompleted").isNull());
        assertEquals(java.sql.Date.valueOf(LocalDate.now().plusDays(4)), orderItem(4).get("next_action_date"));
        // 有问题：算签收（售后单结案后才能进 A5），订单不确认收货；前端随后打开新建售后单
        JsonNode issue = ok(stageRecord(staff, 4, ",\"receiptAnswer\":2,\"issueTypes\":[\"破损\"]"));
        assertEquals("A2", issue.path("stageAfter").asText());
        assertTrue(issue.path("orderCompleted").isMissingNode() || issue.path("orderCompleted").isNull());
        assertEquals(java.sql.Date.valueOf(LocalDate.now()), orderItem(4).get("signed_date"));
        assertEquals("破损", jdbc.queryForObject("select issue_types from customer_followup where tenant_id = 0 and record_type = 1"
                + " order by id desc limit 1", String.class));
        // 回到 A1，继续验证「已收到无问题」
        jdbc.update("update followup_item set signed_date = null where tenant_id = 0 and board = 4");
        sync(C1);
        assertEquals("A1", orderItem(4).get("stage_code"));
        // 已收到无问题：进入 A2，订单确认客户已收货
        JsonNode received = ok(stageRecord(staff, 4, ",\"receiptAnswer\":1"));
        assertEquals("A2", received.path("stageAfter").asText());
        assertTrue(received.path("orderCompleted").asBoolean());
        assertEquals("COMPLETED", jdbc.queryForObject("select progress_code from sales_order where id = ?", String.class, SO1));
        assertEquals(java.sql.Date.valueOf(LocalDate.now()), orderItem(4).get("signed_date"));

        // A2：还没开始用 → 不变；正常使用 → A5
        assertEquals("请选择问题类型", fail(stageRecord(staff, 4, ",\"useAnswer\":2")));
        assertEquals("A2", ok(stageRecord(staff, 4, ",\"useAnswer\":3")).path("stageAfter").asText());
        assertEquals("A5", ok(stageRecord(staff, 4, ",\"useAnswer\":1")).path("stageAfter").asText());

        // A5 校验，满意并已邀请 → A6
        assertEquals("请选择一般的原因", fail(stageRecord(staff, 4, ",\"satisfaction\":2")));
        assertEquals("请选择评价状态", fail(stageRecord(staff, 4, ",\"satisfaction\":1")));
        assertEquals("一般或不满意时不邀请评价", fail(stageRecord(staff, 4, ",\"satisfaction\":3,\"dissatisfiedReason\":\"DELIVERY\","
                + "\"reviewStatus\":2")));
        JsonNode invited = ok(stageRecord(staff, 4, ",\"satisfaction\":1,\"reviewStatus\":2,\"reviewPlatform\":\"阿里国际站\""));
        assertEquals("A6", invited.path("stageAfter").asText());
        assertEquals(java.sql.Date.valueOf(LocalDate.now()), orderItem(4).get("review_invited_at"));
        assertEquals("阿里国际站", orderItem(4).get("review_platform"));

        // A6：还没评 → 继续；已评价 → 售后结束
        assertEquals("评价状态不正确", fail(stageRecord(staff, 4, ",\"reviewStatus\":2")));
        JsonNode notReviewed = ok(stageRecord(staff, 4, ",\"reviewStatus\":6"));
        assertEquals("A6", notReviewed.path("stageAfter").asText());
        assertFalse(notReviewed.path("ended").asBoolean());
        JsonNode done = ok(stageRecord(staff, 4, ",\"reviewStatus\":3"));
        assertTrue(done.path("ended").asBoolean());
        Map<String, Object> a = orderItem(4);
        assertNotNull(a.get("closed_at"));
        assertEquals(3, ((Number) a.get("review_status")).intValue());
        assertEquals("A6", a.get("stage_code"), "已评价从 A6 结束");
        assertEquals("A6", done.path("stageAfter").asText());
    }

    @Test
    void afterSalesRecord_dissatisfiedEnds() throws Exception {
        loginWithResources("fu_staff", MENU, RES_RECORD);
        String staff = token("fu_staff");
        customer(C1, "Müller GmbH", 2, 3);
        order(SO1, "SHIPPED", 6);
        sync(C1);
        jdbc.update("update followup_item set signed_date = ?, used_ok = 1 where board = 4 and target_id = ?", LocalDate.now().minusDays(8), SO1);
        sync(C1);
        assertEquals("A5", orderItem(4).get("stage_code"));
        JsonNode r = ok(stageRecord(staff, 4, ",\"satisfaction\":3,\"dissatisfiedReason\":\"DELIVERY\""));
        assertTrue(r.path("ended").asBoolean());
        assertEquals("DELIVERY", orderItem(4).get("dissatisfied_reason"));
    }

    @Test
    void confirmReceivedByFollowup_threeCases() {
        customer(C1, "Müller GmbH", 2, 3);
        order(SO1, "ORDERED", -1);
        order(SO1 + 1, "SHIPPED", 6);
        order(SO1 + 2, "SHIPPED", 6);
        jdbc.update("update sales_order set progress_code = 'COMPLETED' where id = ?", SO1 + 2);
        TenantContext.runAs(0, () -> {
            // 有型号没出运：不改订单
            assertFalse(salesOrderService.confirmReceivedByFollowup(SO1));
            // 所有型号已出运：确认客户已收货
            assertTrue(salesOrderService.confirmReceivedByFollowup(SO1 + 1));
            // 订单已完成：视为已收货
            assertTrue(salesOrderService.confirmReceivedByFollowup(SO1 + 2));
        });
        assertEquals("ORDERED", jdbc.queryForObject("select progress_code from sales_order where id = ?", String.class, SO1));
        assertEquals("COMPLETED", jdbc.queryForObject("select progress_code from sales_order where id = ?", String.class, SO1 + 1));
        assertNotNull(jdbc.queryForObject("select completed_at from sales_order where id = ?", Object.class, SO1 + 1));
    }

    // ---------------------------------------------------------------- 订单履约、售后评价的列表与总览（第二期 tasks 5.4）

    @Test
    void orderBoards_overviewListAndToday() throws Exception {
        loginWithResources("fu_staff", MENU, RES_RECORD);
        String staff = token("fu_staff");
        customer(C1, "Müller GmbH", 2, 3);
        // F6：今天 DHL 出运
        order(SO1, "SHIPPED", 0);
        // F3：采购超期未到仓
        order(SO1 + 1, "ORDERED", -1);
        jdbc.update("insert into purchase_order (id, tenant_id, po_no, supplier_id, status, ordered_at, expected_ship_date)"
                + " values (99320001, 0, 'PO1', 0, 2, ?, ?)", LocalDateTime.now().minusDays(20), LocalDate.now().minusDays(5));
        jdbc.update("insert into purchase_order_item (id, tenant_id, po_id, so_id, so_item_id, quantity) values (99321001, 0, 99320001, ?, ?, 1)",
                SO1 + 1, SO1 + 1001);
        // A3：已到预计送达日，有处理中的售后单，下一步今天
        order(SO1 + 2, "SHIPPED", 6);
        jdbc.update("insert into after_sales (id, tenant_id, as_no, customer_id, so_id, status, next_action, next_update_at)"
                + " values (99330001, 0, 'AS99330001', ?, ?, 1, '补发出运后告诉客户运单号', ?)", C1, SO1 + 2, LocalDate.now().atTime(9, 0));
        sync(C1);

        JsonNode boards = ok(call(get(BASE + "/overview"), staff)).path("boards");
        JsonNode fulfil = boards.get(2);
        JsonNode after = boards.get(3);
        assertTrue(fulfil.path("available").asBoolean());
        assertTrue(after.path("available").asBoolean());
        assertEquals(2, fulfil.path("total").asInt());
        assertEquals(1, fulfil.path("exceptions").asInt());
        assertEquals("采购中", fulfil.path("groups").get(0).path("name").asText());
        assertEquals(1, fulfil.path("groups").get(0).path("count").asInt());
        assertEquals(1, fulfil.path("groups").get(2).path("count").asInt(), "在途");
        assertEquals("物流跟踪", fulfil.path("groups").get(2).path("stages").get(0).path("name").asText());
        assertEquals(1, after.path("groups").get(2).path("count").asInt(), "问题");

        JsonNode list = ok(call(get(BASE + "/items").param("board", "3"), staff));
        assertEquals(2, list.path("total").asInt());
        JsonNode exceptions = ok(call(get(BASE + "/items").param("board", "3").param("exception", "true"), staff));
        assertEquals(1, exceptions.path("total").asInt());
        JsonNode ex = exceptions.path("records").get(0);
        assertEquals("F3", ex.path("stageCode").asText());
        assertEquals("采购超期未到仓", ex.path("exceptionText").asText());
        JsonNode shipped = null;
        for (JsonNode r : list.path("records")) {
            if ("F6".equals(r.path("stageCode").asText())) {
                shipped = r;
            }
        }
        assertNotNull(shipped);
        assertEquals("物流跟踪", shipped.path("stageName").asText());
        assertEquals("DHL · 按 5 天估算", shipped.path("etaNote").asText());
        assertEquals(LocalDate.now().plusDays(5).toString(), shipped.path("etaDate").asText());

        JsonNode a3 = ok(call(get(BASE + "/items").param("board", "4"), staff)).path("records").get(0);
        assertEquals("A3", a3.path("stageCode").asText());
        assertEquals("AS99330001", a3.path("afterSalesNo").asText());
        assertEquals("处理中", a3.path("afterSalesStatusName").asText());
        assertEquals("补发出运后告诉客户运单号", a3.path("nextAction").asText());
        assertEquals(4, a3.path("nextActionSource").asInt());

        // 客户详情：链路卡片标出订单履约和售后评价；成交客户同时在客户经营（第三期，其他板块在跟时暂停提醒）
        JsonNode summary = ok(call(get(BASE + "/customers/" + C1 + "/summary"), staff));
        assertEquals("[3,4,5]", summary.path("boards").toString());

        // 今日待办不重复列出跟着售后单走的 A3
        JsonNode today = ok(call(get(BASE + "/today"), staff));
        for (JsonNode r : today.path("today")) {
            assertNotEquals("A3", r.path("stageCode").asText());
        }
        for (JsonNode r : today.path("overdue")) {
            assertNotEquals("A3", r.path("stageCode").asText());
        }
    }

    // ---------------------------------------------------------------- 订单履约分部分进度（第二期 tasks 5.5）

    @Test
    void orderParts_twoPurchaseOrdersAndStock() throws Exception {
        loginWithResources("fu_staff", MENU, RES_RECORD);
        String staff = token("fu_staff");
        customer(C1, "Al Noor Trading", 2, 3);
        jdbc.update("insert into sales_order (id, tenant_id, so_no, customer_id, owner_id, currency_code, total_amount, total_amount_cny,"
                + " status, progress_code) values (?, 0, 'SO20261025004', ?, ?, 'USD', 5000, 35500, 1, 'ORDERED')", SO1, C1, SELF);
        // 第 1 部分：PO1 已入库；第 2 部分：PO2 超期未到仓；第 3 部分：库存直发，已在出库单上
        String[][] items = {{"1001", "6ES7 214", "RECEIVED"}, {"1002", "ATV320", "ORDERED"}, {"1003", "E3Z-D61", "RECEIVED"}};
        for (String[] it : items) {
            jdbc.update("insert into sales_order_item (id, tenant_id, so_id, model, quantity, progress_code) values (?, 0, ?, ?, 2, ?)",
                    SO1 + Long.parseLong(it[0]), SO1, it[1], it[2]);
        }
        for (long k : new long[]{1001, 1002}) {
            jdbc.update("insert into purchase_requirement (tenant_id, so_id, so_item_id, model, quantity, status) values (0, ?, ?, 'M', 2, 1)",
                    SO1, SO1 + k);
        }
        for (int n = 1; n <= 2; n++) {
            jdbc.update("insert into purchase_order (id, tenant_id, po_no, supplier_id, status, ordered_at, expected_ship_date)"
                    + " values (?, 0, ?, 0, 2, ?, ?)", 99320000L + n, "PO" + n, LocalDateTime.now().minusDays(20), LocalDate.now().minusDays(3));
            jdbc.update("insert into purchase_order_item (id, tenant_id, po_id, so_id, so_item_id, quantity) values (?, 0, ?, ?, ?, 2)",
                    99321000L + n, 99320000L + n, SO1, SO1 + 1000 + n);
        }
        jdbc.update("insert into outbound_order (id, tenant_id, ob_no, so_id, customer_id, status) values (99340001, 0, 'OB1', ?, ?, 1)",
                SO1, C1);
        jdbc.update("insert into outbound_order_item (tenant_id, outbound_id, so_item_id, quantity) values (0, 99340001, ?, 2)", SO1 + 1003);
        sync(C1);
        Map<String, Object> row = orderItem(3);
        assertEquals(3, ((Number) row.get("part_count")).intValue());
        // 告诉客户新交期
        ok(stageRecord(staff, 3, ",\"noticeMask\":2,\"summary\":\"告诉客户 ATV320 晚 3 天\""));
        ok(stageRecord(staff, 3, ",\"noticeMask\":8"));

        long id = ((Number) row.get("id")).longValue();
        JsonNode vo = ok(call(get(BASE + "/items/" + id + "/parts"), staff));
        JsonNode parts = vo.path("parts");
        assertEquals(3, parts.size());
        Map<String, JsonNode> bySource = new java.util.HashMap<>();
        for (JsonNode p : parts) {
            String poNo = p.path("poNo").asText("");
            bySource.put(poNo.isEmpty() ? p.path("supplier").asText() : poNo, p);
        }
        assertEquals("F4", bySource.get("PO1").path("stageCode").asText());
        assertEquals("F3", bySource.get("PO2").path("stageCode").asText());
        assertEquals("F5", bySource.get("库存直发").path("stageCode").asText());
        assertEquals("包装发货", bySource.get("库存直发").path("stageName").asText());
        assertEquals("ATV320", bySource.get("PO2").path("goods").asText().split(" ")[0]);
        assertFalse(bySource.get("PO2").path("tell").asText().isEmpty());
        // 只列告诉了客户的（「没回复」不算）
        assertEquals(1, vo.path("told").size());
        assertEquals("新的交期 / 送达日", vo.path("told").get(0).path("node").asText());
        assertEquals("告诉客户 ATV320 晚 3 天", vo.path("told").get(0).path("content").asText());
        assertEquals(0, vo.path("batches").size());
    }

    // ---------------------------------------------------------------- 团队视图（第二期 tasks 5.6）

    private static final int RES_TEAM = 110228;

    @Test
    void team_permissionResultsOwnersStagesExceptions() throws Exception {
        LocalDate today = LocalDate.now();
        LocalDateTime thisWeek = today.atStartOfDay().plusMinutes(1);
        LocalDateTime lastWeek = today.with(java.time.DayOfWeek.MONDAY).minusDays(3).atTime(10, 0);
        customer(C1, "Müller GmbH", 1, 5);
        customer(C2, "Al Noor Trading", 3, 2);
        jdbc.update("update customer set owner_id = ? where id = ?", OTHER, C2);
        jdbc.update("update user_basic set dept_id = 77 where id = ?", OTHER);

        // 新商机：本周 1、上周 1
        opportunity(9301, C1, "S2");
        opportunity(9302, C1, "S2");
        jdbc.update("update opportunity set create_time = ? where id = 9301", thisWeek);
        jdbc.update("update opportunity set create_time = ?, closed_at = ? where id = 9302", lastWeek, lastWeek);
        // 发出报价：9311 本周第一次发；9312 上周第一次发、本周发新版本（不算本周）
        quote(9311, C1, "100", "710", thisWeek, SELF);
        quote(9312, C1, "100", "710", thisWeek, SELF);
        jdbc.update("insert into quotation_version (tenant_id, quotation_id, version_no, sent_at, status) values (0, 9311, 1, ?, 2),"
                + " (0, 9312, 1, ?, 2), (0, 9312, 2, ?, 2)", thisWeek, lastWeek, thisWeek);
        // 逾期 5 天、连续延后 3 次的报价跟进（李娜，3 条）
        for (long q = 9321; q <= 9323; q++) {
            quote(q, C2, "500", "3550", LocalDateTime.now().minusDays(9), OTHER);
        }
        // 订单：本周 SO1（复购：客户之前有已完成的订单 SO1+1）
        order(SO1, "SHIPPED", 0);
        order(SO1 + 1, "SHIPPED", 40);
        jdbc.update("update sales_order set create_time = ?, progress_code = 'COMPLETED', completed_at = ? where id = ?",
                LocalDateTime.now().minusDays(45), LocalDateTime.now().minusDays(30), SO1 + 1);
        jdbc.update("update sales_order set create_time = ? where id = ?", thisWeek, SO1);
        sync(C1);
        sync(C2);
        jdbc.update("update followup_item set next_action_date = ?, snooze_count = 3 where customer_id = ? and board = 2",
                today.minusDays(5), C2);
        jdbc.update("update followup_item set next_action_date = ? where customer_id = ? and board = 2", today.plusDays(1), C1);
        // F6 已停留 15 天（参考 10）
        jdbc.update("update followup_item set stage_since = ? where board = 3 and target_id = ?", LocalDateTime.now().minusDays(15), SO1);
        // 按时送达：本周签收 2 单，1 单晚于首次估算
        jdbc.update("insert into followup_item (tenant_id, board, target_type, target_id, customer_id, owner_id, stage_code, stage_group,"
                + " signed_date, eta_first, satisfaction, dissatisfied_reason, closed_at, close_reason) values"
                + " (0, 4, 5, 99399001, ?, ?, 'A5', 10, ?, ?, 0, '', null, 0),"
                + " (0, 4, 5, 99399002, ?, ?, 'A5', 10, ?, ?, 3, 'DELIVERY', ?, 5)",
                C1, SELF, today, today, C1, SELF, today, today.minusDays(2), LocalDateTime.now());
        long a5 = jdbc.queryForObject("select id from followup_item where target_id = 99399001", Long.class);
        jdbc.update("insert into customer_followup (tenant_id, record_type, customer_id, followup_item_id, target_type, target_id,"
                + " review_status, operator_id, create_time) values (0, 1, ?, ?, 5, 99399001, 3, ?, ?), (0, 1, ?, ?, 5, 99399001, 2, ?, ?)",
                C1, a5, SELF, thisWeek, C1, a5, SELF, lastWeek);

        // 没有「查看团队」权限：403
        loginWithResources("fu_staff", MENU, RES_RECORD);
        assertEquals(403, call(get(BASE + "/team"), token("fu_staff")).path("code").asInt());

        loginAsAdmin("fu_admin");
        String admin = token("fu_admin");
        JsonNode t = ok(call(get(BASE + "/team"), admin));
        assertEquals("week", t.path("period").asText());
        JsonNode r = t.path("results");
        assertEquals(1, r.path("newOpportunities").path("value").asInt());
        assertEquals(1, r.path("newOpportunities").path("prev").asInt());
        assertEquals(1, r.path("quotationsSent").path("value").asInt(), "9312 第一次发送在上周");
        assertEquals(1, r.path("quotationsSent").path("prev").asInt());
        assertEquals(1, r.path("orders").path("count").asInt());
        assertEquals(0, new BigDecimal("7100.00").compareTo(r.path("orders").path("amountCny").decimalValue()));
        assertEquals(1, r.path("repeat").path("count").asInt());
        assertEquals(2, r.path("delivered").asInt());
        assertEquals(1, r.path("onTime").asInt());
        assertEquals(1, r.path("reviewed").asInt());
        assertEquals(0, r.path("invited").asInt(), "已邀请的那条在上周");

        // 按负责人：李娜逾期 3、多次延后 3 → 需要关注，排在前面
        JsonNode lina = t.path("owners").get(0);
        assertEquals(OTHER, lina.path("ownerId").asLong());
        assertEquals(3, lina.path("boards").get(1).asInt());
        assertEquals(3, lina.path("overdue").asInt());
        assertEquals(3, lina.path("manySnoozes").asInt());
        assertTrue(lina.path("attention").asBoolean());
        JsonNode me = t.path("owners").get(1);
        assertEquals(1, me.path("records").asInt());
        assertEquals(1, me.path("prevRecords").asInt());
        assertFalse(me.path("attention").asBoolean());

        // 卡在哪一步：F6 平均 15 天 / 参考 10，标橙
        JsonNode f6 = null;
        for (JsonNode st : t.path("stages")) {
            if ("F6".equals(st.path("stageCode").asText())) {
                f6 = st;
            }
        }
        assertNotNull(f6);
        assertEquals(1, f6.path("count").asInt());
        assertEquals(15, f6.path("avgDays").asInt());
        assertEquals(10, f6.path("refDays").asInt());
        assertTrue(f6.path("over").asBoolean());
        assertEquals(19, t.path("stages").size(), "三个板块共 19 个阶段");

        // 需要主管看一眼：3 逾期 + 3 多次延后 + 1 不满意；默认前 5 条，按客户价值（Müller 等级 S 在前）
        JsonNode ex = t.path("exceptions");
        assertEquals(7, ex.path("total").asInt());
        assertEquals(5, ex.path("items").size());
        assertEquals("DISSATISFIED", ex.path("items").get(0).path("type").asText());
        assertEquals("不满意 · 原因：交期", ex.path("items").get(0).path("text").asText());
        assertTrue(ex.path("items").get(1).path("text").asText().startsWith("逾期 5 天"));
        assertEquals(7, ok(call(get(BASE + "/team").param("exceptions", "all"), admin)).path("exceptions").path("items").size());

        // 按部门：只看李娜
        JsonNode dept = ok(call(get(BASE + "/team").param("deptId", "77"), admin));
        assertEquals(1, dept.path("owners").size());
        assertEquals(OTHER, dept.path("owners").get(0).path("ownerId").asLong());
        assertEquals(0, dept.path("results").path("orders").path("count").asInt());

        // 有团队权限、数据权限仅本人：只看到自己
        loginWithResources("fu_staff", MENU, RES_RECORD, RES_TEAM);
        JsonNode self = ok(call(get(BASE + "/team").param("period", "last-week"), token("fu_staff")));
        assertEquals(today.with(java.time.DayOfWeek.MONDAY).minusWeeks(1).toString(), self.path("from").asText());
        for (JsonNode o : self.path("owners")) {
            assertNotEquals(OTHER, o.path("ownerId").asLong());
        }
        assertEquals("时间范围不正确", call(get(BASE + "/team").param("period", "year"), token("fu_staff")).path("message").asText());
    }
}
