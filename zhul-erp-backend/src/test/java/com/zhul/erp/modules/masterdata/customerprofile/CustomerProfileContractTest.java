package com.zhul.erp.modules.masterdata.customerprofile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.common.constants.RedisKeyConstants;
import com.zhul.erp.common.utils.JwtUtils;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.aitask.constants.AiTaskStatus;
import com.zhul.erp.modules.aitask.entity.AiTaskDO;
import com.zhul.erp.modules.aitask.translation.ContactNameTranslations;
import com.zhul.erp.modules.masterdata.customerprofile.service.CustomerNightlyJob;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * 客户档案（spec master-data/customer-profile、country-profile、customer-merge）：联系人与主联系人同步、画像修改与来源、
 * 完整度、系统推导、经营等级（RFM、调整、排序）、国家资料、疑似重复与合并。
 */
@AutoConfigureMockMvc
class CustomerProfileContractTest extends IntegrationTestBase {

    private static final String BASE = "/api/v1/masterdata/customers";
    private static final String COUNTRY = "/api/v1/masterdata/country-profiles";

    @Autowired private MockMvc mvc;
    @Autowired private JwtUtils jwtUtils;
    @Autowired private StringRedisTemplate redis;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private CustomerNightlyJob nightlyJob;
    @Autowired private ContactNameTranslations contactNameTranslations;

    private String admin;

    @BeforeEach
    void setUp() {
        cleanup();
        loginAsAdmin("it_cp_admin");
        admin = token("it_cp_admin");
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    private void cleanup() {
        jdbc.update("delete from inquiry_item where tenant_id = 0 and customer_inquiry_id in (select id from customer_inquiry where remark = 'it_cp')");
        jdbc.update("delete from customer_inquiry where tenant_id = 0 and remark = 'it_cp'");
        jdbc.update("delete from sales_order where tenant_id = 0 and so_no like 'ITCP%'");
        jdbc.update("delete from fin_customer_credit_entry where tenant_id = 0 and remark = 'it_cp'");
        jdbc.update("delete from customer_contact where tenant_id = 0");
        jdbc.update("delete from customer_field_source where tenant_id = 0");
        jdbc.update("delete from customer_dup_dismiss where tenant_id = 0");
        jdbc.update("delete from country_profile where tenant_id = 0");
        jdbc.update("delete from customer_party where tenant_id = 0");
        jdbc.update("delete from customer where tenant_id = 0");
        jdbc.update("delete from exchange_rate where tenant_id = 0 and currency_code = 'USD'");
        jdbc.update("delete from ai_task where tenant_id = 0 and skill_id = ?", ContactNameTranslations.SKILL_ID);
        removeAiConfig(0);
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

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder b, String body) {
        return b.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private JsonNode ok(JsonNode res) {
        assertEquals(0, res.path("code").asInt(), res.toString());
        return res.path("data");
    }

    private long customer(String name, String country, String contact, String whatsapp) throws Exception {
        String body = "{\"name\":\"" + name + "\",\"country\":\"" + country + "\",\"customerRole\":2,\"currency\":\"USD\""
                + (contact == null ? "" : ",\"contactName\":\"" + contact + "\"")
                + (whatsapp == null ? "" : ",\"whatsapp\":\"" + whatsapp + "\"") + "}";
        return ok(call(json(post(BASE), body), admin)).path("id").asLong();
    }

    private JsonNode profile(long id) throws Exception {
        return ok(call(get(BASE + "/" + id + "/profile"), admin));
    }

    private JsonNode saveProfile(long id, String body) throws Exception {
        return call(json(put(BASE + "/" + id + "/profile"), body), admin);
    }

    private void order(long customerId, String no, LocalDate date, String currency, String amount, String cny, int status) {
        jdbc.update("insert into sales_order (tenant_id, so_no, source, sales_date, customer_id, owner_id, currency_code, total_amount, "
                + "total_amount_cny, status) values (0, ?, 2, ?, ?, 99000002, ?, ?, ?, ?)", no, date, customerId, currency, amount, cny, status);
    }

    private int translateTasks() {
        return jdbc.queryForObject("select count(*) from ai_task where tenant_id = 0 and skill_id = ?", Integer.class,
                ContactNameTranslations.SKILL_ID);
    }

    private void translated(String key, String text) {
        AiTaskDO task = new AiTaskDO();
        task.setTenantId(0);
        task.setSkillId(ContactNameTranslations.SKILL_ID);
        task.setStatus(AiTaskStatus.COMPLETED);
        task.setOutput("{\"items\":[{\"key\":\"" + key + "\",\"text\":\"" + text + "\"}]}");
        // 生产中由 AI 回调按任务所属租户执行（AiTaskServiceImpl.handleCallback 的 runAs）
        TenantContext.runAs(task.getTenantId(), () -> contactNameTranslations.handle(task));
    }

    @Test
    void contactNameCn_manualKept_emptyTranslated_primarySynced() throws Exception {
        seedAiConfig(0);
        long id = customer("Pacific Controls Pty Ltd", "Australia", "James Walker", null);
        long james = ok(call(get(BASE + "/" + id + "/contacts"), admin)).path(0).path("id").asLong();
        assertEquals(1, translateTasks(), "主联系人中文名为空时提交翻译");

        // 回调：联系人中文名回填，主联系人同步到客户；再次回调不覆盖
        translated("contact:" + james, "詹姆斯");
        assertEquals("詹姆斯", ok(call(get(BASE + "/" + id + "/contacts"), admin)).path(0).path("nameCn").asText());
        assertEquals("詹姆斯", ok(call(get(BASE + "/" + id + "/detail"), admin)).path("contactNameCn").asText());
        translated("contact:" + james, "杰姆斯");
        assertEquals("詹姆斯", jdbc.queryForObject("select name_cn from customer_contact where id = ?", String.class, james));

        // 手填中文名：不提交翻译，回调也不覆盖；不传 nameCn 时保持原值
        long anna = ok(call(json(post(BASE + "/" + id + "/contacts"), "{\"name\":\"Anna Lee\",\"nameCn\":\"李安娜\"}"), admin))
                .path("id").asLong();
        assertEquals(1, translateTasks(), "已手填中文名不提交翻译");
        translated("contact:" + anna, "安娜");
        ok(call(json(put(BASE + "/" + id + "/contacts/" + anna), "{\"name\":\"Anna Lee\",\"title\":\"Buyer\"}"), admin));
        assertEquals("李安娜", jdbc.queryForObject("select name_cn from customer_contact where id = ?", String.class, anna));
        assertEquals("詹姆斯", jdbc.queryForObject("select contact_name_cn from customer where id = ?", String.class, id),
                "非主联系人不写客户");

        // 设为主联系人后客户的主联系人中文名跟着变；客户表单改中文名同步到主联系人
        ok(call(put(BASE + "/" + id + "/contacts/" + anna + "/primary"), admin));
        assertEquals("李安娜", jdbc.queryForObject("select contact_name_cn from customer where id = ?", String.class, id));
        assertTrue(call(json(put(BASE + "/" + id), "{\"name\":\"Pacific Controls Pty Ltd\",\"country\":\"Australia\","
                + "\"customerRole\":2,\"status\":1,\"currency\":\"USD\",\"contactName\":\"Anna Lee\",\"contactNameCn\":"
                + "\"" + "x".repeat(101) + "\"}"), admin).path("message").asText().contains("联系人中文名不能超过100个字符"), "中文名超长被拒");
        ok(call(json(put(BASE + "/" + id), "{\"name\":\"Pacific Controls Pty Ltd\",\"country\":\"Australia\","
                + "\"customerRole\":2,\"status\":1,\"currency\":\"USD\",\"contactName\":\"Anna Lee\",\"contactNameCn\":\"李安娜娜\"}"), admin));
        assertEquals("李安娜娜", jdbc.queryForObject("select name_cn from customer_contact where id = ?", String.class, anna));
        assertEquals(1, translateTasks(), "英文名没变不重复提交");

        // 清空中文名保存 = 重新翻译
        ok(call(json(put(BASE + "/" + id + "/contacts/" + anna), "{\"name\":\"Anna Lee\",\"nameCn\":\"\"}"), admin));
        assertEquals(2, translateTasks(), "清空中文名后重新提交翻译");
        assertEquals("", jdbc.queryForObject("select contact_name_cn from customer where id = ?", String.class, id), "主联系人同步清空");
    }

    @Test
    void contacts_primarySyncAndDeleteRules() throws Exception {
        long id = customer("Pacific Controls Pty Ltd", "Australia", "James Walker", "+61 400 111 222");
        JsonNode contacts = ok(call(get(BASE + "/" + id + "/contacts"), admin));
        assertEquals(1, contacts.size(), "客户表单里的主联系人进入联系人列表");
        assertTrue(contacts.path(0).path("primary").asBoolean());
        long james = contacts.path(0).path("id").asLong();

        long anna = ok(call(json(post(BASE + "/" + id + "/contacts"), "{\"name\":\"Anna Lee\",\"email\":\"anna@pc.com.au\","
                + "\"jobRole\":2,\"decisionRole\":2,\"commStyle\":3}"), admin)).path("id").asLong();
        ok(call(put(BASE + "/" + id + "/contacts/" + anna + "/primary"), admin));
        assertEquals("Anna Lee", jdbc.queryForObject("select contact_name from customer where id = ?", String.class, id));
        assertEquals("anna@pc.com.au", jdbc.queryForObject("select email_key from customer where id = ?", String.class, id));
        assertEquals(1, ok(call(get(BASE + "/page").param("contactName", "anna"), admin)).path("total").asInt());

        assertEquals("请先把其他联系人设为主联系人", call(delete(BASE + "/" + id + "/contacts/" + anna), admin).path("message").asText());
        ok(call(delete(BASE + "/" + id + "/contacts/" + james), admin));
        ok(call(delete(BASE + "/" + id + "/contacts/" + anna), admin));
        assertEquals("", jdbc.queryForObject("select contact_name from customer where id = ?", String.class, id), "删掉最后一个联系人后清空");
        assertEquals(3, jdbc.queryForObject("select count(*) from customer_field_source where customer_id = ? and field_key like ?",
                Integer.class, id, "contact." + anna + ".%"), "联系人画像字段记来源");
    }

    @Test
    void profile_updateValidateSourcesAndCompleteness() throws Exception {
        long id = customer("Nordic Pack AB", "Sweden", "Erik", null);
        JsonNode p = profile(id);
        assertEquals(0, p.path("completeness").path("mask").asInt() & 1, "刚建的客户基础画像还没完成");
        assertFalse(p.path("completeness").path("complete").asBoolean());

        assertTrue(saveProfile(id, "{\"fields\":[\"estAnnualPurchase\"],\"estAnnualPurchase\":-500}").path("message").asText()
                .contains("年度采购量估算不能为负"));
        assertEquals("「name」不能在这里修改", saveProfile(id, "{\"fields\":[\"name\"]}").path("message").asText());
        assertEquals("公司规模不正确", saveProfile(id, "{\"fields\":[\"companySize\"],\"companySize\":9}").path("message").asText());
        assertEquals("请填写定金比例", saveProfile(id, "{\"fields\":[\"paymentMethod\"],\"paymentMethod\":3}").path("message").asText());

        ok(saveProfile(id, "{\"fields\":[\"customerRole\",\"industry\",\"website\",\"companySize\",\"potentialRating\"],"
                + "\"customerRole\":5,\"industry\":4,\"website\":\"https://nordicpack.se\",\"companySize\":2,\"potentialRating\":4}"));
        assertEquals(5, jdbc.queryForObject("select customer_role from customer where id = ?", Integer.class, id), "公司类型就是客户角色");
        p = profile(id);
        assertEquals(1, p.path("completeness").path("mask").asInt() & 1, "基础画像完成");
        assertEquals(1, p.path("sources").path("companySize").path("source").asInt(), "来源为人工");

        ok(saveProfile(id, "{\"fields\":[\"mainProducts\",\"qualityReq\",\"priceSensitivity\",\"customsAbility\",\"paymentMethod\"],"
                + "\"mainProducts\":\"Packaging machines\",\"qualityReq\":2,\"priceSensitivity\":1,\"customsAbility\":1,"
                + "\"paymentMethod\":3,\"depositRatio\":30}"));
        ok(saveProfile(id, "{\"fields\":[\"currentBrands\",\"designAbility\",\"protocols\",\"voltages\",\"repairAbility\"],"
                + "\"currentBrands\":[{\"name\":\"Siemens\"}],\"designAbility\":1,\"protocols\":[2,1],\"voltages\":[5,7],\"repairAbility\":1}"));
        long contact = ok(call(get(BASE + "/" + id + "/contacts"), admin)).path(0).path("id").asLong();
        ok(call(json(put(BASE + "/" + id + "/contacts/" + contact), "{\"name\":\"Erik\",\"jobRole\":3,\"decisionRole\":1,\"commStyle\":1}"), admin));
        ok(saveProfile(id, "{\"fields\":[\"city\",\"timezone\"],\"city\":\"Malmo\",\"timezone\":\"Europe/Stockholm\"}"));
        p = profile(id);
        assertEquals(4, p.path("completeness").path("done").asInt(), "国家资料还没维护");
        assertEquals("countryProfile", p.path("completeness").path("missing").path(0).path("keys").path(0).asText());

        ok(call(json(put(COUNTRY), "{\"country\":\"sweden\",\"voltages\":[5,7],\"fxControl\":1}"), admin));
        assertEquals(31, jdbc.queryForObject("select profile_mask from customer where id = ?", Integer.class, id), "维护国家资料后已建档");
        assertEquals(1, ok(call(get(BASE + "/page").param("profileStatus", "done"), admin)).path("total").asInt());
        assertEquals(0, ok(call(get(BASE + "/page").param("missingModules", "4"), admin)).path("total").asInt());
    }

    @Test
    void completeness_contactModuleLabelsContactName() throws Exception {
        long id = customer("No Contact GmbH", "Germany", null, null);
        JsonNode missing = profile(id).path("completeness").path("missing");
        JsonNode m4 = null;
        for (JsonNode m : missing) {
            if (m.path("module").asInt() == 4) {
                m4 = m;
            }
        }
        assertNotNull(m4, "没有联系人时模块④未完成");
        assertEquals("联系人姓名", m4.path("labels").path(0).asText(), "④ 缺的是联系人姓名，不是客户名称");
    }

    @Test
    void derived_brandsAndInquiryStats() throws Exception {
        long id = customer("Delta Industrial Supply", "United States", null, null);
        jdbc.update("insert into customer_inquiry (tenant_id, inquiry_code, customer_id, inquiry_date, quote_deadline, status, remark) "
                + "values (0, 'ITCP1', ?, '2026-10-01', '2026-10-02', 3, 'it_cp')", id);
        long inq = jdbc.queryForObject("select max(id) from customer_inquiry", Long.class);
        for (String[] b : new String[][] {{"Siemens", "siemens"}, {"SIEMENS", "siemens"}, {"西门子", "siemens"}, {"Omron", "omron"}}) {
            jdbc.update("insert into inquiry_item (tenant_id, customer_inquiry_id, line_no, brand, brand_key, original_model, confirmed_model, quantity) "
                    + "values (0, ?, 1, ?, ?, 'X', 'X', 1)", inq, b[0], b[1]);
        }
        JsonNode p = profile(id);
        JsonNode top = p.path("derived").path("topBrands");
        assertEquals(3, top.path(0).path("count").asInt(), "同一品牌的不同写法合并计数");
        assertEquals(1, p.path("derived").path("inquiryCount").asInt());
        assertEquals(9, p.path("sources").path("currentBrands").path("source").asInt(), "没手填时常用品牌来自系统");
    }

    @Test
    void grade_rfmAdjustNightlyAndSort() throws Exception {
        jdbc.update("insert into exchange_rate (tenant_id, currency_code, rate) values (0, 'USD', 7.100000)");
        LocalDate today = LocalDate.now();
        long high = customer("Servitop sa de cv", "Mexico", null, null);
        for (int i = 0; i < 20; i++) {
            order(high, "ITCPH" + i, today.minusDays(2 + i), "USD", "2238.22", "15891.36", 1);
        }
        long small = customer("David Berglund", "Sweden", null, null);
        order(small, "ITCPS1", today.minusDays(5), "USD", "320.00", "2272.00", 1);
        order(small, "ITCPS2", today.minusDays(3), "USD", "99999.00", "709992.90", 2);
        long euro = customer("Euro Buyer GmbH", "Germany", null, null);
        order(euro, "ITCPE1", today.minusDays(5), "EUR", "4600.00", "35500.00", 1);
        long none = customer("Ahmed Trading LLC", "United Arab Emirates", null, null);

        JsonNode r = ok(call(get(BASE + "/" + high + "/rfm"), admin));
        assertEquals(0, new java.math.BigDecimal("5.0").compareTo(r.path("score").decimalValue()));
        assertEquals(1, r.path("finalGrade").asInt(), "S");
        assertEquals(7, r.path("followDays").asInt());
        r = ok(call(get(BASE + "/" + small + "/rfm"), admin));
        assertEquals(2, r.path("f").asInt(), "已取消的订单不计");
        assertEquals(0, new java.math.BigDecimal("2.6").compareTo(r.path("score").decimalValue()));
        assertEquals(3, r.path("calc").asInt(), "B");
        r = ok(call(get(BASE + "/" + euro + "/rfm"), admin));
        assertEquals(0, new java.math.BigDecimal("5000.00").compareTo(r.path("usdInYear").decimalValue()), "EUR 订单按人民币 ÷ 当前美元汇率折算");
        assertEquals(4, r.path("m").asInt());
        assertEquals(0, ok(call(get(BASE + "/" + none + "/rfm"), admin)).path("calc").asInt(), "未成交");
        assertTrue(call(json(put(BASE + "/" + none + "/grade"), "{\"adjust\":1,\"reason\":\"x\"}"), admin).path("message").asText().contains("没有成交"));

        assertEquals("请填写调整原因", call(json(put(BASE + "/" + small + "/grade"), "{\"adjust\":1}"), admin).path("message").asText());
        ok(call(json(put(BASE + "/" + small + "/grade"), "{\"adjust\":1,\"reason\":\"系统集成商，采购频繁\"}"), admin));
        assertEquals(2, jdbc.queryForObject("select biz_grade from customer where id = ?", Integer.class, small), "B 上调为 A");

        jdbc.update("update sales_order set sales_date = ? where so_no = 'ITCPS1'", today.minusDays(400));
        // 走定时任务入口：没有租户上下文，由任务自己按租户逐个重算（audit SEC-011）
        TenantContext.clear();
        nightlyJob.run();
        assertEquals(4, jdbc.queryForObject("select biz_grade_calc from customer where id = ?", Integer.class, small), "重算为 C");
        assertEquals(3, jdbc.queryForObject("select biz_grade from customer where id = ?", Integer.class, small), "调整方向保留，最终 B");

        JsonNode page = ok(call(get(BASE + "/page").param("sortField", "bizGrade").param("sortOrder", "asc").param("pageSize", "10"), admin));
        List<Long> order = new ArrayList<>();
        page.path("records").forEach(x -> order.add(x.path("id").asLong()));
        assertEquals(List.of(high, euro, small, none), order, "S、A、B 在前，未成交排最后");
    }

    @Test
    void countryProfile_listAndStrictWarning() throws Exception {
        customer("India One Pvt Ltd", "India", null, null);
        customer("India Two Pvt Ltd", "India", null, null);
        customer("Lagos Power Ltd", "Nigeria", null, null);
        assertEquals("国家不在系统国家清单里", call(json(put(COUNTRY), "{\"country\":\"Atlantis\"}"), admin).path("message").asText());
        ok(call(json(put(COUNTRY), "{\"country\":\"Nigeria\",\"voltages\":[3,5,7],\"fxControl\":3,\"fxNote\":\"美元购汇排队\"}"), admin));
        JsonNode list = ok(call(get(COUNTRY), admin));
        assertEquals("India", list.path(0).path("country").asText(), "未维护的排在前面");
        assertTrue(list.path(0).path("id").isMissingNode() || list.path(0).path("id").isNull(), "未维护的国家没有 id");
        assertEquals(2, list.path(0).path("customerCount").asInt());
        assertEquals(1, ok(call(get(COUNTRY).param("filter", "strict"), admin)).size());
        long lagos = jdbc.queryForObject("select id from customer where name = 'Lagos Power Ltd'", Long.class);
        assertEquals(3, profile(lagos).path("countryProfile").path("fxControl").asInt(), "客户详情带出国家资料");
    }

    @Test
    void merge_groupsMoveDocumentsAndBlockFinance() throws Exception {
        long keep = customer("Delta Industrial Supply", "United States", "Mike", "+1 305 555 0101");
        long dup = customer("Delta Ind. Co", "United States", "Mike Ross", "+1 (305) 555-0101");
        ok(saveProfile(dup, "{\"fields\":[\"industry\"],\"industry\":8}"));
        jdbc.update("insert into customer_inquiry (tenant_id, inquiry_code, customer_id, inquiry_date, quote_deadline, status, remark) "
                + "values (0, 'ITCP2', ?, '2026-10-01', '2026-10-02', 3, 'it_cp')", dup);
        order(dup, "ITCPM1", LocalDate.now().minusDays(1), "USD", "100.00", "710.00", 1);

        JsonNode groups = ok(call(get(BASE + "/duplicates"), admin));
        assertEquals(1, groups.size());
        assertEquals("PHONE", groups.path(0).path("reason").asText());

        JsonNode preview = ok(call(get(BASE + "/merge-preview").param("targetId", String.valueOf(keep)).param("sourceId", String.valueOf(dup)), admin));
        assertEquals(1, preview.path("documents").path("询盘").asInt());
        assertEquals(1, preview.path("documents").path("销售订单").asInt());
        assertEquals(0, preview.path("contacts").size(), "同 WhatsApp 的联系人不重复建");

        jdbc.update("insert into fin_customer_credit_entry (tenant_id, customer_id, currency_code, entry_type, amount, amount_cny, lot_time, remark) "
                + "values (0, ?, 'USD', 1, 10.00, 71.00, now(), 'it_cp')", dup);
        assertEquals("这个客户有客户余额或退款记录，请先联系财务处理",
                call(json(post(BASE + "/merge"), "{\"targetId\":" + keep + ",\"sourceId\":" + dup + "}"), admin).path("message").asText());
        jdbc.update("delete from fin_customer_credit_entry where remark = 'it_cp'");

        ok(call(json(post(BASE + "/merge"), "{\"targetId\":" + keep + ",\"sourceId\":" + dup + "}"), admin));
        assertEquals(keep, jdbc.queryForObject("select customer_id from customer_inquiry where inquiry_code = 'ITCP2'", Long.class));
        assertEquals(keep, jdbc.queryForObject("select customer_id from sales_order where so_no = 'ITCPM1'", Long.class));
        assertEquals(8, jdbc.queryForObject("select industry from customer where id = ?", Integer.class, keep), "空字段补上");
        assertTrue(jdbc.queryForObject("select remark from customer where id = ?", String.class, dup).contains("已并入"));
        assertEquals(0, ok(call(get(BASE + "/duplicates"), admin)).size());
    }
}
