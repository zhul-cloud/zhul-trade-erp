package com.zhul.erp.modules.supply;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.common.constants.RedisKeyConstants;
import com.zhul.erp.common.utils.JwtUtils;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.supply.job.SupplyDailyJob;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * 供应链第二期供应商开发（tasks 3.1–3.4、4.1–4.5、4.7）：
 * 合格供应商口径（第 90 / 91 天、无货、停用、采购订单）、线索、主动拓展唯一、建开发任务的路径、
 * 候选不能手动标合格、回价后自动合格与证据过期退回、矩阵与板块数字一致、没有「查看货源信息」不返回名称。
 */
@AutoConfigureMockMvc
class SupplierDevContractTest extends IntegrationTestBase {

    private static final String API = "/api/v1/supply";
    private static final int MENU_FOLLOWUP = 110236;
    private static final String KEY = "itdev1";

    @Autowired private MockMvc mvc;
    @Autowired private JwtUtils jwtUtils;
    @Autowired private StringRedisTemplate redis;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private SupplyDailyJob job;

    private long brandId;
    private long category;
    private long taskId;
    private final Map<String, Long> sup = new LinkedHashMap<>();

    @BeforeEach
    void data() {
        cleanup();
        jdbc.update("insert into product_brand (tenant_id, brand_name, status) values (0, 'ITDEVBRAND', 1)");
        brandId = jdbc.queryForObject("select id from product_brand where brand_name = 'ITDEVBRAND'", Long.class);
        jdbc.update("insert into product_category (tenant_id, category_code, category_name, category_name_zh, parent_id, status,"
                + " create_by) values (0, 'itdev_l1', 'itdev_l1', 'IT一级', null, 1, 'itdev')");
        long parent = jdbc.queryForObject("select id from product_category where category_code = 'itdev_l1'", Long.class);
        jdbc.update("insert into product_category (tenant_id, category_code, category_name, category_name_zh, parent_id, status,"
                + " create_by) values (0, 'itdev_l2', 'itdev_l2', 'IT伺服', ?, 1, 'itdev')", parent);
        category = jdbc.queryForObject("select id from product_category where category_code = 'itdev_l2'", Long.class);
        // A 第 90 天有价、E 第 91 天有价、B 停用、C 只有无货、D 只登记主营产品、F 采购订单、G 留给候选
        for (String s : List.of("A", "B", "C", "D", "E", "F", "G")) {
            jdbc.update("insert into supplier (tenant_id, supplier_code, name, status) values (0, ?, ?, ?)",
                    "ITDEV-" + s, "IT开发" + s, "B".equals(s) ? 0 : 1);
            sup.put(s, jdbc.queryForObject("select id from supplier where supplier_code = ?", Long.class, "ITDEV-" + s));
        }
        jdbc.update("insert into supplier_product_scope (tenant_id, supplier_id, brand_id) values (0, ?, ?)", sup.get("D"), brandId);
        jdbc.update("insert into sc_demand (tenant_id, brand_id, category_id, model_key, model, scope_key, status, priority,"
                + " coverage_status, suggested_coverage) values (0, ?, ?, ?, 'ITDEV-1', 'm:itdev1', 2, 3, 1, 1)", brandId, category, KEY);
        jdbc.update("insert into sourcing_task (tenant_id, task_code) values (0, 'ITDEV-T1')");
        taskId = jdbc.queryForObject("select id from sourcing_task where task_code = 'ITDEV-T1'", Long.class);
        quote("A", 89, false);
        quote("E", 90, false);
        quote("B", 0, false);
        quote("C", 0, true);
        jdbc.update("insert into purchase_order (tenant_id, po_no, shop_name, supplier_id, status, order_date, currency_code,"
                + " exchange_rate) values (0, 'ITDEV-PO1', 'x', ?, 2, ?, 'CNY', 1)", sup.get("F"), LocalDate.now().minusDays(10));
        long po = jdbc.queryForObject("select id from purchase_order where po_no = 'ITDEV-PO1'", Long.class);
        jdbc.update("insert into purchase_order_item (tenant_id, po_id, model, brand, quantity, unit_price, net_price_cny)"
                + " values (0, ?, 'ITDEV-1', 'ITDEVBRAND', 1, 10, 10)", po);
    }

    @AfterEach
    void cleanup() {
        jdbc.update("delete from sc_dev_candidate where tenant_id = 0 and name like 'IT%'");
        jdbc.update("delete from sc_expansion_scope where tenant_id = 0 and reason like 'IT%'");
        jdbc.update("delete from sc_followup_log where tenant_id = 0 and gap_project_id in"
                + " (select id from sc_gap_project where name like 'ITDEV%')");
        jdbc.update("delete from document_log where tenant_id = 0 and doc_type in ('SX', 'GP')");
        jdbc.update("delete from sc_gap_project where tenant_id = 0 and name like 'ITDEV%'");
        jdbc.update("delete from sc_followup_log where tenant_id = 0 and source_doc_no = 'ITDEV-T1'");
        jdbc.update("delete from sc_demand_signal where tenant_id = 0 and demand_id in"
                + " (select id from (select id from sc_demand where tenant_id = 0 and model_key = ?) x)", KEY);
        jdbc.update("delete from sc_demand where tenant_id = 0 and model_key = ?", KEY);
        jdbc.update("delete from sourcing_quote where task_id in (select id from sourcing_task where task_code = 'ITDEV-T1')");
        jdbc.update("delete from sourcing_task where task_code = 'ITDEV-T1'");
        jdbc.update("delete from purchase_order_item where po_id in (select id from purchase_order where po_no = 'ITDEV-PO1')");
        jdbc.update("delete from purchase_order where po_no = 'ITDEV-PO1'");
        jdbc.update("delete from supplier_product_scope where supplier_id in (select id from supplier where supplier_code like 'ITDEV-%')");
        jdbc.update("delete from supplier where supplier_code like 'ITDEV-%'");
        jdbc.update("delete from product_category where category_code in ('itdev_l2', 'itdev_l1')");
        jdbc.update("delete from product_brand where brand_name = 'ITDEVBRAND'");
    }

    @Test
    void listQualifiedLeadsAndPermissions() throws Exception {
        loginAsAdmin("it_dev_admin");
        String admin = token("it_dev_admin");
        JsonNode g = group(admin, "gap");
        assertEquals(3, g.path("heat").asInt(), "有高优先级需求对象 → 高热度");
        assertEquals(3, g.path("required").asInt());
        assertEquals(2, g.path("qualified").asInt(), "A（第 90 天）与 F（采购订单）合格；E 第 91 天、B 停用、C 无货不算：" + g);
        assertEquals(1, g.path("gap").asInt());
        assertEquals("todo", g.path("stage").asText());
        assertEquals(1, g.path("leads").asInt(), "D 只登记主营产品，算线索");
        assertEquals(List.of("IT开发A", "IT开发F"), names(g.path("qualifiedSuppliers")).stream().sorted().toList());

        JsonNode cells = ok(call(get(API + "/matrix").param("mode", "supplier").param("brandId", String.valueOf(brandId)), admin))
                .path("cells");
        assertEquals(1, cells.size(), cells.toString());
        assertEquals(2, cells.get(0).path("qualified").asInt(), "矩阵与板块数字一致");
        assertEquals(1, cells.get(0).path("gap").asInt());

        // 主动拓展：同范围只能有一条
        Map<String, Object> e = Map.of("brandId", brandId, "categoryId", category, "heat", 1, "reason", "IT明年重点");
        ok(call(json(post(API + "/dev/expansions"), write(e)), admin));
        assertNotEquals(0, call(json(post(API + "/dev/expansions"), write(e)), admin).path("code").asInt(), "重复加入被拒");
        g = group(admin, "gap");
        assertEquals("both", g.path("source").asText());
        assertEquals(3, g.path("heat").asInt(), "取较高的一档");

        loginWithResources("it_dev_viewer", MENU_FOLLOWUP);
        String viewer = token("it_dev_viewer");
        g = group(viewer, "gap");
        assertEquals(2, g.path("qualified").asInt());
        assertEquals(0, g.path("qualifiedSuppliers").size(), "没有「查看货源信息」不返回名称");
        assertEquals(0, g.path("leadSuppliers").size());
        assertEquals(403, perform(json(post(API + "/dev/expansions"), write(e)), viewer).getStatus(), "没有「维护供应商开发」不能加入");
    }

    @Test
    void devTaskCandidatesAutoQualifyAndExpire() throws Exception {
        loginAsAdmin("it_dev_admin");
        String admin = token("it_dev_admin");
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("brandId", brandId);
        p.put("name", "ITDEV 伺服开发");
        p.put("goal", "再开发 1 家");
        p.put("ownerId", 99000002L);
        p.put("dueDate", LocalDate.now().plusDays(30).toString());
        p.put("supplierTarget", 1);
        assertNotEquals(0, call(json(post(API + "/projects"), write(p)), admin).path("code").asInt(), "开发目标须选细分品类");
        p.put("categoryId", category);
        p.put("supplierTarget", 11);
        assertEquals(400, perform(json(post(API + "/projects"), write(p)), admin).getStatus(), "目标最多 10 家");
        p.put("supplierTarget", 1);
        long project = ok(call(json(post(API + "/projects"), write(p)), admin)).asLong();
        assertEquals("doing", group(admin, "gap").path("stage").asText());

        long cand = ok(call(json(post(API + "/projects/" + project + "/candidates"),
                write(Map.of("name", "IT展会代理", "source", 2))), admin)).asLong();
        Map<String, Object> rec = new LinkedHashMap<>();
        rec.put("candidateIds", List.of(cand));
        rec.put("content", "电话联系");
        rec.put("stage", 4);
        assertNotEquals(0, call(json(post(API + "/candidates/records"), write(rec)), admin).path("code").asInt(), "不能手动标合格");
        rec.put("stage", 3);
        assertNotEquals(0, call(json(post(API + "/candidates/records"), write(rec)), admin).path("code").asInt(), "下一步必填");
        rec.put("nextAction", "等对方发代理证明");
        rec.put("nextActionDate", LocalDate.now().plusDays(3).toString());
        assertEquals(1, ok(call(json(post(API + "/candidates/records"), write(rec)), admin)).path("changed").asInt());
        assertEquals(3, stage(cand));

        ok(call(json(post(API + "/candidates/" + cand + "/link"), write(Map.of("supplierId", sup.get("G")))), admin));
        assertEquals(3, stage(cand), "关联了但还没有证据");
        quote("G", 0, false);
        runJob();
        assertEquals(4, stage(cand), "回价后定时任务标为合格");
        assertEquals(1, jdbc.queryForObject("select evidence_type from sc_dev_candidate where id = ?", Integer.class, cand));
        JsonNode pv = ok(call(get(API + "/projects/" + project), admin));
        assertEquals(1, pv.path("devQualified").asInt());
        assertEquals(1, pv.path("dev").path("qualified").asInt());
        assertTrue(pv.path("dev").path("suggestClose").asBoolean(), "达到目标提示结束：" + pv.path("dev"));
        assertEquals(2, pv.path("dev").path("others").size(), "范围内其他合格供应商 A、F");
        assertEquals("done", group(admin, "all").path("stage").asText(), "合格 3 家，已达标");

        jdbc.update("update sourcing_quote set quoted_at = date_sub(now(), interval 100 day) where supplier_id = ?", sup.get("G"));
        runJob();
        assertEquals(3, stage(cand), "证据过期退回已报价");

        assertNotEquals(0, call(json(put(API + "/projects/" + project + "/target"), "{}"), admin).path("code").asInt(),
                "没有型号时不能去掉开发目标");
        ok(call(json(put(API + "/projects/" + project + "/target"), "{\"target\":2}"), admin));
        assertEquals(2, jdbc.queryForObject("select supplier_target from sc_gap_project where id = ?", Integer.class, project));
        assertEquals("再开发 1 家", jdbc.queryForObject("select goal from sc_gap_project where id = ?", String.class, project),
                "自己写的目标说明不随目标家数改");
        jdbc.update("update sc_gap_project set goal = '开发 2 家合格供应商' where id = ?", project);
        ok(call(json(put(API + "/projects/" + project + "/target"), "{\"target\":3}"), admin));
        assertEquals("开发 3 家合格供应商", jdbc.queryForObject("select goal from sc_gap_project where id = ?", String.class, project),
                "默认生成的目标说明跟着目标家数改");

        // 候选全部放弃、合格不够目标 → 负责人今日待办出现「补充候选」
        Map<String, Object> ab = new LinkedHashMap<>();
        ab.put("candidateIds", List.of(cand));
        ab.put("content", "只做翻新件");
        ab.put("stage", 5);
        assertNotEquals(0, call(json(post(API + "/candidates/records"), write(ab)), admin).path("code").asInt(), "放弃须写原因");
        ab.put("abandonReason", "只做翻新件");
        ok(call(json(post(API + "/candidates/records"), write(ab)), admin));
        JsonNode today = ok(call(get(API + "/today"), admin));
        assertTrue(today.path("devItems").toString().contains("补充候选供应商"), today.path("devItems").toString());
    }

    private void quote(String s, int daysAgo, boolean noStock) {
        jdbc.update("insert into sourcing_quote (tenant_id, task_id, supplier_id, brand, model, model_key, currency_code, unit_price,"
                + " no_stock, status, quoted_at) values (0, ?, ?, 'ITDEVBRAND', 'ITDEV-1', ?, 'CNY', ?, ?, 2,"
                + " timestamp(date_sub(curdate(), interval ? day), '10:00:00'))",
                taskId, sup.get(s), KEY, noStock ? 0 : 12.5, noStock ? 1 : 0, daysAgo);
    }

    private JsonNode group(String token, String stage) throws Exception {
        JsonNode groups = ok(call(get(API + "/dev/groups").param("stage", stage).param("ownerId", "0")
                .param("brandId", String.valueOf(brandId)), token)).path("groups");
        assertEquals(1, groups.size(), groups.toString());
        return groups.get(0);
    }

    private int stage(long cand) {
        return jdbc.queryForObject("select stage from sc_dev_candidate where id = ?", Integer.class, cand);
    }

    private static List<String> names(JsonNode arr) {
        List<String> out = new java.util.ArrayList<>();
        arr.forEach(a -> out.add(a.path("name").asText()));
        return out;
    }

    private void runJob() {
        TenantContext.runAs(0, () -> job.runTenant(0, LocalDate.now()));
    }

    private String token(String username) {
        String token = jwtUtils.generateToken(Map.of("tenantId", 0), username, 3600);
        redis.opsForValue().set(RedisKeyConstants.TOKEN_PREFIX + token, "1");
        return token;
    }

    private org.springframework.mock.web.MockHttpServletResponse perform(MockHttpServletRequestBuilder request, String token)
            throws Exception {
        request.header("Authorization", "Bearer " + token);
        return mvc.perform(request).andReturn().getResponse();
    }

    private JsonNode call(MockHttpServletRequestBuilder request, String token) throws Exception {
        return objectMapper.readTree(perform(request, token).getContentAsString(StandardCharsets.UTF_8));
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder b, String body) {
        return b.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private JsonNode ok(JsonNode res) {
        assertEquals(0, res.path("code").asInt(), res.toString());
        return res.path("data");
    }

    private String write(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
