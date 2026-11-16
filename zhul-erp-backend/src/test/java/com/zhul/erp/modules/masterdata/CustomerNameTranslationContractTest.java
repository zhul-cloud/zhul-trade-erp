package com.zhul.erp.modules.masterdata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.common.constants.RedisKeyConstants;
import com.zhul.erp.common.utils.JwtUtils;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.aitask.constants.AiTaskStatus;
import com.zhul.erp.modules.aitask.entity.AiTaskDO;
import com.zhul.erp.modules.aitask.support.AiTaskKeys;
import com.zhul.erp.modules.aitask.translation.ContactNameTranslations;
import com.zhul.erp.modules.aitask.translation.CustomerNameTranslations;
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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * feedback-1116-batch1：客户英文名称自动翻译中文（spec master-data/customer）、编辑页「AI 翻译」按钮，
 * 以及客户列表按档案完整度排序与建档统计（spec master-data/customer-profile「客户档案完整度」）。
 * 编排服务不可达，任务只落库；回调由用例按编排服务的输出格式直接交给结果处理。
 */
@AutoConfigureMockMvc
class CustomerNameTranslationContractTest extends IntegrationTestBase {

    private static final String BASE = "/api/v1/masterdata/customers";
    private static final String SKILL = CustomerNameTranslations.SKILL_ID;

    @Autowired private MockMvc mvc;
    @Autowired private JwtUtils jwtUtils;
    @Autowired private StringRedisTemplate redis;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private CustomerNameTranslations translations;

    private String admin;

    @BeforeEach
    void setUp() {
        cleanup();
        seedAiConfig(0);
        loginAsAdmin("it_cn_admin");
        admin = token("it_cn_admin");
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    private void cleanup() {
        jdbc.update("delete from customer_contact where tenant_id = 0");
        jdbc.update("delete from customer_field_source where tenant_id = 0");
        jdbc.update("delete from customer_party where tenant_id = 0");
        jdbc.update("delete from followup_item where tenant_id = 0");
        jdbc.update("delete from opportunity_stage_log where tenant_id = 0");
        jdbc.update("delete from opportunity where tenant_id = 0");
        jdbc.update("delete from customer where tenant_id = 0");
        jdbc.update("delete from ai_task where tenant_id = 0 and skill_id in (?, ?)", SKILL, ContactNameTranslations.SKILL_ID);
        removeAiConfig(0);
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

    private JsonNode ok(JsonNode res) {
        assertEquals(0, res.path("code").asInt(), res.toString());
        return res.path("data");
    }

    private String body(String name, String nameCn, String translation) throws Exception {
        Map<String, Object> m = new java.util.HashMap<>(Map.of("name", name, "country", "Germany", "customerRole", 2,
                "currency", "USD", "status", 1));
        if (nameCn != null) {
            m.put("nameCn", nameCn);
        }
        if (translation != null) {
            m.put("nameCnTranslation", translation);
        }
        return objectMapper.writeValueAsString(m);
    }

    private long create(String name, String nameCn) throws Exception {
        return ok(call(post(BASE).contentType(MediaType.APPLICATION_JSON).content(body(name, nameCn, null)))).path("id").asLong();
    }

    private void update(long id, String name, String nameCn, String translation) throws Exception {
        ok(call(put(BASE + "/" + id).contentType(MediaType.APPLICATION_JSON).content(body(name, nameCn, translation))));
    }

    private int tasks() {
        return jdbc.queryForObject("select count(*) from ai_task where tenant_id = 0 and skill_id = ?", Integer.class, SKILL);
    }

    /** 提交给编排服务的 key（带校验码，回调原样带回） */
    private static String key(long customerId, String name) {
        return AiTaskKeys.sign(CustomerNameTranslations.customerKey(customerId, name));
    }

    private void callback(String key, String text) {
        AiTaskDO task = new AiTaskDO();
        task.setTenantId(0);
        task.setSkillId(SKILL);
        task.setStatus(AiTaskStatus.COMPLETED);
        task.setRequestedBy(99000002L);
        task.setOutput("{\"items\":[{\"key\":\"" + key + "\",\"text\":\"" + text + "\"}]}");
        TenantContext.runAs(0, () -> translations.handle(task));
    }

    private String nameCn(long id) {
        return jdbc.queryForObject("select name_cn from customer where id = ?", String.class, id);
    }

    private Integer source(long id) {
        List<Integer> s = jdbc.queryForList("select source from customer_field_source where customer_id = ? and field_key = 'nameCn'",
                Integer.class, id);
        return s.isEmpty() ? null : s.get(0);
    }

    @Test
    void newCustomer_translatedInBackground_aiSourceUpdatesOnRename_manualKept() throws Exception {
        long id = create("Hoorain HTF Limited", null);
        assertEquals(1, tasks(), "新建时提交一次翻译");
        String first = key(id, "Hoorain HTF Limited");
        callback(first, "胡拉因 HTF 有限公司");
        assertEquals("胡拉因 HTF 有限公司", nameCn(id));
        assertEquals(5, source(id), "来源记 AI 翻译");
        JsonNode detail = ok(call(get(BASE + "/" + id + "/detail")));
        assertEquals(5, detail.path("nameCnSource").asInt());

        // 只改大小写、首尾空格不算变化
        update(id, " hoorain htf limited ", "胡拉因 HTF 有限公司", null);
        assertEquals(1, tasks());

        // 上次是 AI 翻译的：改英文名称后重新翻译；旧任务迟到的结果丢弃
        update(id, "Hoorain HTF Trading Limited", "胡拉因 HTF 有限公司", null);
        assertEquals(2, tasks());
        callback(first, "旧译名");
        assertEquals("胡拉因 HTF 有限公司", nameCn(id), "英文名称已改，旧结果不写");
        String second = key(id, "Hoorain HTF Trading Limited");
        callback(second, "胡拉因 HTF 贸易有限公司");
        assertEquals("胡拉因 HTF 贸易有限公司", nameCn(id));

        // 人工改过中文名称：之后改英文名称不再提交；翻译途中人工改过的回调也不覆盖
        update(id, "Hoorain HTF Trading Limited", "胡拉因", null);
        assertEquals(1, source(id), "人工修改记人工");
        update(id, "Hoorain HTF Group Limited", "胡拉因", null);
        assertEquals(2, tasks(), "人工中文名不再提交翻译");
        callback(second, "又一个译名");
        assertEquals("胡拉因", nameCn(id));
    }

    @Test
    void manualNameOnCreate_notTranslated_andNoAiConfigSkips() throws Exception {
        long manual = create("Siam Controls Co., Ltd.", "暹罗控制");
        assertEquals(0, tasks(), "人工填写的中文名称不翻译");
        assertEquals(1, source(manual));

        removeAiConfig(0);
        long other = create("Delta Industrial Supply", null);
        assertTrue(other > 0, "没配置 AI 时照常保存");
        assertEquals(0, tasks());
    }

    @Test
    void translateButton_previewOnly_sourceByWhetherEdited() throws Exception {
        JsonNode started = ok(call(post(BASE + "/name-translation").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"ABC Automation GmbH\"}")));
        long taskId = started.path("taskId").asLong();
        assertNotEquals(0, taskId);
        assertNotEquals(AiTaskStatus.COMPLETED, ok(call(get(BASE + "/name-translation/" + taskId))).path("status").asInt(),
                "还没回调时未完成");
        String key = AiTaskKeys.sign("preview");
        jdbc.update("update ai_task set status = ?, output = ? where id = ?", AiTaskStatus.COMPLETED,
                "{\"items\":[{\"key\":\"" + key + "\",\"text\":\"ABC 自动化有限公司\"}]}", taskId);
        JsonNode done = ok(call(get(BASE + "/name-translation/" + taskId)));
        assertEquals("ABC 自动化有限公司", done.path("results").get(0).path("text").asText());
        callback(key, "不该写到任何客户");

        // 用了按钮的译名保存：来源 AI 翻译，且不再重复提交翻译
        long id = ok(call(post(BASE).contentType(MediaType.APPLICATION_JSON)
                .content(body("ABC Automation GmbH", "ABC 自动化有限公司", "ABC 自动化有限公司")))).path("id").asLong();
        assertEquals(5, source(id));
        assertEquals(1, tasks(), "只有按钮那一次");
        // 译名改过再保存：记人工
        update(id, "ABC Automation GmbH", "ABC 自动化", "ABC 自动化有限公司");
        assertEquals(1, source(id));

        assertEquals(400, mvc.perform(post(BASE + "/name-translation").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}")).andReturn().getResponse().getStatus());
    }

    /** 登记商机填了客户名称：客户中文名称按公司名翻译，不再用联系人名音译（spec crm/opportunity「登记新商机」） */
    @Test
    void opportunityWithCompanyName_translatesCompanyNotContact() throws Exception {
        ok(call(post("/api/v1/crm/opportunities").contentType(MediaType.APPLICATION_JSON).content(
                "{\"customerName\":\"Acme Trading LLC\",\"contactName\":\"John\",\"country\":\"germany\","
                        + "\"sourceChannel\":5,\"firstContactDate\":\"" + java.time.LocalDate.now() + "\"}")));
        long id = jdbc.queryForObject("select id from customer where tenant_id = 0 and name = 'Acme Trading LLC'", Long.class);
        assertEquals(1, tasks(), "按公司名提交翻译");
        // 只剩主联系人中文名的音译，不再给客户中文名称音译联系人名
        assertEquals(1, jdbc.queryForObject("select count(*) from ai_task where tenant_id = 0 and skill_id = ?", Integer.class,
                ContactNameTranslations.SKILL_ID));
        callback(key(id, "Acme Trading LLC"), "艾克米贸易有限公司");
        assertEquals("艾克米贸易有限公司", nameCn(id));
    }

    @Test
    void profileSortAndCounts() throws Exception {
        removeAiConfig(0);
        long full = create("Full Profile GmbH", null);
        long half = create("Half Profile GmbH", null);
        long none = create("Empty Profile GmbH", null);
        jdbc.update("update customer set profile_mask = 31 where id = ?", full);
        jdbc.update("update customer set profile_mask = 5 where id = ?", half);
        jdbc.update("update customer set profile_mask = 0 where id = ?", none);

        JsonNode asc = ok(call(get(BASE + "/page").param("sortField", "profile").param("sortOrder", "asc"))).path("records");
        assertEquals(List.of(none, half, full), List.of(asc.get(0).path("id").asLong(), asc.get(1).path("id").asLong(),
                asc.get(2).path("id").asLong()), "升序时缺得最多的在前");
        JsonNode desc = ok(call(get(BASE + "/page").param("sortField", "profile").param("sortOrder", "desc"))).path("records");
        assertEquals(full, desc.get(0).path("id").asLong());

        JsonNode counts = ok(call(get(BASE + "/profile-counts")));
        assertEquals(3, counts.path("all").asLong());
        assertEquals(1, counts.path("done").asLong());
        assertEquals(2, counts.path("pending").asLong());
        assertEquals(2, ok(call(get(BASE + "/page").param("profileStatus", "pending"))).path("total").asInt());
    }
}
