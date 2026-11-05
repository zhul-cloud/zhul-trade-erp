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

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * 客户经营的接口契约（第三期 tasks 3.x）：记跟进的四种客户情况与阶段、作战卡、交叉销售、战略客户与年度计划、列表与链路卡片、团队指标。
 * 测试账号在平台租户（tenant 0），账号 ID 即 {@link #SELF}。订单记为历史迁移（migrated = 1），不生成订单履约对象。
 */
@AutoConfigureMockMvc
class AccountApiContractTest extends IntegrationTestBase {

    private static final String BASE = "/api/v1/followups";
    private static final long SELF = 99000002L;
    private static final int MENU = 110226;
    private static final int RES_RECORD = 110227;
    private static final long C1 = 99400001L;
    private static final long[] PEERS = {99400002L, 99400003L, 99400004L};
    private static final LocalDate TODAY = LocalDate.now();

    @Autowired private MockMvc mvc;
    @Autowired private JwtUtils jwtUtils;
    @Autowired private StringRedisTemplate redis;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private FollowupSync followupSync;

    private long nextOrderId = 99410001L;

    @BeforeEach
    void setUp() {
        cleanup();
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    private void cleanup() {
        for (String t : new String[]{"followup_item", "customer_followup", "customer_annual_plan", "customer_inquiry", "sales_order_item",
                "sales_order"}) {
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

    private void customer(long id, int bizGrade) {
        jdbc.update("insert into customer (id, tenant_id, customer_code, name, country, owner_id, biz_grade) values (?, 0, ?, ?, 'Germany', ?, ?)",
                id, "CU" + id, "C" + id, SELF, bizGrade);
    }

    private void order(long customerId, LocalDate salesDate, int amountCny, String... categories) {
        long id = nextOrderId++;
        jdbc.update("insert into sales_order (id, tenant_id, so_no, customer_id, owner_id, currency_code, total_amount, total_amount_cny,"
                + " status, progress_code, sales_date, migrated) values (?, 0, ?, ?, ?, 'USD', 1, ?, 1, 'COMPLETED', ?, 1)",
                id, "SO" + id, customerId, SELF, BigDecimal.valueOf(amountCny), salesDate);
        for (String c : categories) {
            jdbc.update("insert into sales_order_item (tenant_id, so_id, brand, model, quantity, category, amount_cny)"
                    + " values (0, ?, 'Siemens', ?, 2, ?, ?)", id, "M-" + c, c, BigDecimal.valueOf(amountCny));
        }
    }

    private void sync(long customerId) {
        TenantContext.runAs(0, () -> followupSync.resync(customerId, TODAY, false));
    }

    private Map<String, Object> account() {
        return jdbc.queryForMap("select * from followup_item where tenant_id = 0 and board = 5 and customer_id = ?", C1);
    }

    private long accountId() {
        return ((Number) account().get("id")).longValue();
    }

    private JsonNode record(String token, long itemId, String extra) throws Exception {
        return call(json(post(BASE + "/records"), "{\"customerId\":" + C1 + ",\"itemId\":" + itemId + ",\"situation\":0,"
                + "\"nextAction\":\"问下季度备件计划\",\"nextActionDate\":\"" + TODAY.plusDays(7) + "\",\"channel\":1,"
                + "\"idempotencyKey\":\"" + UUID.randomUUID() + "\"" + extra + "}"), token);
    }

    // ---------------------------------------------------------------- 记一次跟进（tasks 3.4）

    @Test
    void record_fourAnswers_moveStages() throws Exception {
        customer(C1, 2);
        order(C1, TODAY.minusDays(30), 1000, "PLC");
        sync(C1);
        long id = accountId();
        LocalDate dueBefore = ((java.sql.Date) account().get("next_action_date")).toLocalDate();
        loginWithResources("fu_staff", MENU, RES_RECORD);
        String staff = token("fu_staff");

        assertEquals("请选择客户情况", record(staff, id, "").path("message").asText());
        assertEquals("只有「聊了新品类」才填品类", record(staff, id, ",\"accountAnswer\":1,\"crossCategory\":\"HMI\"").path("message").asText());
        assertEquals("只有战略客户才有季度回顾", record(staff, id, ",\"accountAnswer\":4,\"quarterReview\":true").path("message").asText());

        JsonNode cross = ok(record(staff, id, ",\"accountAnswer\":2,\"crossCategory\":\" HMI \""));
        assertEquals("G4", cross.path("stageBefore").asText());
        assertEquals("G6", cross.path("stageAfter").asText());
        Map<String, Object> it = account();
        assertEquals(1, ((Number) it.get("account_cross")).intValue());
        assertEquals("HMI", it.get("cross_category"));
        Map<String, Object> rec = jdbc.queryForMap("select * from customer_followup where id = ?", cross.path("recordId").asLong());
        assertEquals(2, ((Number) rec.get("account_answer")).intValue());
        assertEquals(16, ((Number) rec.get("situation")).intValue(), "客户情况位由答案给出");
        assertEquals("HMI", rec.get("cross_category"));
        assertEquals(dueBefore, ((java.sql.Date) rec.get("due_date")).toLocalDate(), "记下当时的到期日，团队视图算按时");
        assertEquals(2, ((Number) it.get("next_action_source")).intValue());
        assertEquals(TODAY.plusDays(7), ((java.sql.Date) it.get("next_action_date")).toLocalDate(), "业务员定的日期优先于系统节奏");

        assertEquals("请选择没有需求的原因", record(staff, id, ",\"accountAnswer\":3").path("message").asText());
        JsonNode none = ok(record(staff, id, ",\"accountAnswer\":3,\"noNeedReason\":\"NOT_RELEVANT\""));
        assertEquals("G4", none.path("stageAfter").asText(), "暂不相关，回到需求挖掘");
        assertEquals(0, ((Number) account().get("account_cross")).intValue());

        JsonNode need = ok(record(staff, id, ",\"accountAnswer\":1"));
        assertEquals("G5", need.path("stageAfter").asText());
        assertEquals(1, ((Number) account().get("account_need")).intValue());

        ok(record(staff, id, ",\"accountAnswer\":4"));
        assertEquals(1, ((Number) account().get("no_reply_streak")).intValue());
        ok(record(staff, id, ",\"accountAnswer\":3,\"noNeedReason\":\"NO_BUDGET\""));
        assertEquals(0, ((Number) account().get("no_reply_streak")).intValue(), "有回复就清零");
        assertEquals("G4", account().get("stage_code"));
    }

    @Test
    void record_newNeed_clearedOnceInquiryCreated() throws Exception {
        customer(C1, 2);
        order(C1, TODAY.minusDays(30), 1000, "PLC");
        sync(C1);
        loginWithResources("fu_staff", MENU, RES_RECORD);
        ok(record(token("fu_staff"), accountId(), ",\"accountAnswer\":1"));
        jdbc.update("insert into customer_inquiry (id, tenant_id, inquiry_code, customer_id, owner_id, status, source, inquiry_date,"
                + " quote_deadline, create_time) values (99420001, 0, 'INQ99420001', ?, ?, 1, 1, ?, ?, ?)", C1, SELF, TODAY, TODAY.plusDays(3),
                java.time.LocalDateTime.now().plusSeconds(1));
        sync(C1);
        Map<String, Object> it = account();
        assertEquals(0, ((Number) it.get("account_need")).intValue(), "建了询盘，标记清零");
        assertEquals("G5", it.get("stage_code"), "询盘在跟，仍是复购机会");
    }

    @Test
    void record_accountFieldsOnOtherBoard_isStageMismatch() throws Exception {
        customer(C1, 2);
        jdbc.update("insert into customer_inquiry (id, tenant_id, inquiry_code, customer_id, owner_id, status, source, inquiry_date,"
                + " quote_deadline) values (99420002, 0, 'INQ99420002', ?, ?, 1, 1, ?, ?)", C1, SELF, TODAY, TODAY.plusDays(3));
        sync(C1);
        long salesItem = jdbc.queryForObject("select id from followup_item where tenant_id = 0 and board = 2 and customer_id = ?",
                Long.class, C1);
        loginWithResources("fu_staff", MENU, RES_RECORD);
        JsonNode res = call(json(post(BASE + "/records"), "{\"customerId\":" + C1 + ",\"itemId\":" + salesItem + ",\"situation\":1,"
                + "\"accountAnswer\":1,\"nextAction\":\"x\",\"nextActionDate\":\"" + TODAY.plusDays(1) + "\",\"idempotencyKey\":\""
                + UUID.randomUUID() + "\"}"), token("fu_staff"));
        assertEquals("FOLLOWUP_STAGE_MISMATCH", res.path("data").path("errorCode").asText(), res.toString());
    }

    // ---------------------------------------------------------------- 作战卡、交叉销售（tasks 3.2）

    @Test
    void card_showsPatternLastOrderAndCrossSell() throws Exception {
        customer(C1, 2);
        order(C1, TODAY.minusDays(40), 1000, "PLC");
        // 三个也买 PLC 的客户：3 个买过 HMI、2 个买过变频器、1 个买过伺服（不够 2 个不推荐）
        for (int i = 0; i < PEERS.length; i++) {
            customer(PEERS[i], 3);
            order(PEERS[i], TODAY.minusDays(20), 500, "PLC", "HMI");
            if (i < 2) {
                order(PEERS[i], TODAY.minusDays(10), 500, "变频器");
            } else {
                order(PEERS[i], TODAY.minusDays(10), 500, "伺服");
            }
        }
        sync(C1);
        loginAsAdmin("fu_admin");
        JsonNode card = ok(call(get(BASE + "/accounts/" + C1), token("fu_admin")));
        assertEquals("A", card.path("bizGrade").asText());
        assertEquals("数据不足", card.path("patternText").asText());
        assertEquals("Siemens M-PLC ×2", card.path("lastOrderItems").asText());
        assertEquals(TODAY.minusDays(40).toString(), card.path("lastOrderDate").asText());
        assertEquals(2, card.path("crossSell").size());
        assertEquals("HMI", card.path("crossSell").get(0).path("category").asText());
        assertEquals(3, card.path("crossSell").get(0).path("customers").asInt());
        assertEquals("变频器", card.path("crossSell").get(1).path("category").asText());
        assertFalse(card.path("strategic").asBoolean());
        assertFalse(card.path("suggestStrategic").asBoolean(), "A 级不提示设为战略客户");
        assertTrue(card.path("plan").isNull() || card.path("plan").isMissingNode(), "不是战略客户没有年度计划");
    }

    // ---------------------------------------------------------------- 战略客户与年度计划（tasks 3.5）

    @Test
    void strategic_thenPlan_percentHalfUp() throws Exception {
        customer(C1, 1);
        order(C1, LocalDate.of(TODAY.getYear(), 1, 1), 30000, "PLC");
        sync(C1);
        loginAsAdmin("fu_admin");
        String admin = token("fu_admin");
        assertTrue(ok(call(get(BASE + "/accounts/" + C1), admin)).path("suggestStrategic").asBoolean(), "S 级提示设为战略客户");
        assertEquals("只有战略客户才能填写年度计划", call(json(put(BASE + "/accounts/" + C1 + "/plan"), "{\"targetAmount\":90000}"), admin)
                .path("message").asText());

        ok(call(json(put(BASE + "/accounts/" + C1 + "/strategic"), "{\"strategic\":true}"), admin));
        assertEquals("G7", account().get("stage_code"));
        assertEquals(1, jdbc.queryForObject("select count(*) from customer_followup where customer_id = ? and title = '设为战略客户'",
                Integer.class, C1));

        // 30000 ÷ 45000 = 66.67%，HALF_UP 为 67（向下取整会是 66）
        JsonNode plan = ok(call(json(put(BASE + "/accounts/" + C1 + "/plan"),
                "{\"targetAmount\":45000,\"focus\":\" Siemens PLC \",\"note\":\"\"}"), admin));
        assertEquals(TODAY.getYear(), plan.path("planYear").asInt());
        assertEquals(0, new BigDecimal("45000.00").compareTo(plan.path("targetAmount").decimalValue()));
        assertEquals(0, new BigDecimal("30000.00").compareTo(plan.path("dealtAmount").decimalValue()));
        assertEquals(67, plan.path("percent").asInt());
        assertNotEquals(0, call(json(put(BASE + "/accounts/" + C1 + "/plan"), "{\"targetAmount\":1.005}"), admin).path("code").asInt(),
                "金额最多 2 位小数");
        assertEquals("Siemens PLC", plan.path("focus").asText());
        ok(call(json(put(BASE + "/accounts/" + C1 + "/plan"), "{\"targetAmount\":40000}"), admin));
        assertEquals(1, jdbc.queryForObject("select count(*) from customer_annual_plan where customer_id = ?", Integer.class, C1),
                "同一年只有一份，再次保存为更新");
        assertEquals(75, ok(call(get(BASE + "/accounts/" + C1), admin)).path("plan").path("percent").asInt(), "30000 ÷ 40000");
        assertNotEquals(0, call(json(put(BASE + "/accounts/" + C1 + "/plan"), "{\"targetAmount\":-1}"), admin).path("code").asInt());

        ok(record(admin, accountId(), ",\"accountAnswer\":3,\"noNeedReason\":\"NO_PROJECT\",\"quarterReview\":true"));
        assertEquals(TODAY, jdbc.queryForObject("select last_review_date from customer where id = ?", LocalDate.class, C1),
                "季度回顾更新回顾日期");
        assertEquals(TODAY.plusDays(90).toString(), ok(call(get(BASE + "/accounts/" + C1), admin)).path("nextReviewDate").asText());
        assertEquals("G7", account().get("stage_code"), "战略客户没有需求时仍是长期合作");

        ok(call(json(put(BASE + "/accounts/" + C1 + "/strategic"), "{\"strategic\":false}"), admin));
        assertEquals("G4", account().get("stage_code"), "取消战略客户离开长期合作");
    }

    @Test
    void strategic_requiresDealtCustomer() throws Exception {
        customer(C1, 0);
        loginAsAdmin("fu_admin");
        assertEquals("只有成交客户才能设为战略客户", call(json(put(BASE + "/accounts/" + C1 + "/strategic"), "{\"strategic\":true}"),
                token("fu_admin")).path("message").asText());
    }

    // ---------------------------------------------------------------- 列表、链路卡片、团队指标（tasks 3.3、3.6）

    @Test
    void listOverviewAndTeam_forAccountBoard() throws Exception {
        customer(C1, 3);
        order(C1, TODAY.minusDays(250), 1000, "PLC");
        customer(PEERS[0], 2);
        order(PEERS[0], TODAY.minusDays(20), 1000, "PLC");
        sync(C1);
        sync(PEERS[0]);
        loginAsAdmin("fu_admin");
        String admin = token("fu_admin");

        JsonNode overview = ok(call(get(BASE + "/overview").param("ownerScope", "all"), admin));
        JsonNode board5 = overview.path("boards").get(4);
        assertTrue(board5.path("available").asBoolean());
        assertEquals(2, board5.path("customers").asInt());
        assertEquals(1, board5.path("dormant").asInt());
        assertEquals(4, board5.path("groups").size());
        assertEquals("需求挖掘", board5.path("groups").get(0).path("name").asText());
        assertEquals(2, board5.path("groups").get(0).path("count").asInt());
        assertEquals("需求挖掘", board5.path("groups").get(0).path("stages").get(0).path("name").asText(), "阶段名来自 SOP");

        JsonNode all = ok(call(get(BASE + "/items").param("board", "5").param("ownerScope", "all"), admin));
        assertEquals(2, all.path("total").asLong());
        JsonNode dormant = ok(call(get(BASE + "/items").param("board", "5").param("dormant", "true").param("ownerScope", "all"), admin));
        assertEquals(1, dormant.path("total").asLong());
        JsonNode row = dormant.path("records").get(0);
        assertEquals(C1, row.path("customerId").asLong());
        assertEquals("需求挖掘", row.path("stageName").asText());
        assertEquals(250, row.path("dormantDays").asInt());
        assertEquals(TODAY.minusDays(250).toString(), row.path("lastOrderDate").asText());
        assertEquals(TODAY.minusDays(250).toString(), row.path("lastContactDate").asText(), "没有跟进记录时取最近一单");
        assertTrue(row.path("contactOverdue").asBoolean(), "超过 B 级联系间隔");
        assertTrue(row.path("followDays").asInt() > 0);
        assertFalse(row.path("strategic").asBoolean());
        assertFalse(row.path("sopHint").asText().isEmpty(), "SOP 默认动作");

        // 团队指标：PEERS[0] 的对象今天到期且还没跟（应联系 1、按时 0）；沉睡 1
        jdbc.update("update followup_item set next_action_date = ? where tenant_id = 0 and board = 5 and customer_id = ?", TODAY, PEERS[0]);
        jdbc.update("update followup_item set next_action_date = ? where tenant_id = 0 and board = 5 and customer_id = ?",
                TODAY.plusDays(400), C1);
        jdbc.update("insert into customer_inquiry (id, tenant_id, inquiry_code, customer_id, owner_id, status, source, inquiry_date,"
                + " quote_deadline) values (99420003, 0, 'INQ99420003', ?, ?, 1, 1, ?, ?)", PEERS[0], SELF, TODAY, TODAY.plusDays(3));
        JsonNode team = ok(call(get(BASE + "/team").param("period", "week"), admin)).path("results");
        assertEquals(1, team.path("accountDue").asInt(), team.toString());
        assertEquals(0, team.path("accountOnTime").asInt());
        assertEquals(0, team.path("accountOnTimePercent").asInt());
        assertEquals(1, team.path("accountInquiries").path("value").asLong(), "老客户新询盘");
        assertEquals(1, team.path("dormant").asInt());
        assertEquals(0, team.path("activated").asInt());
    }
}
