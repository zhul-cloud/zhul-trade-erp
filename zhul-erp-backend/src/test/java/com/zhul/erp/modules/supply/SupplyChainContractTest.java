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

/**
 * 供应链第一期完整链路（tasks 4.8）：询盘汇入 → 进入矩阵 → 无货提醒 → 记一次处理 → 专项进度；
 * 没有「查看货源信息」不返回供应商；只能看到本租户的需求对象。
 */
@AutoConfigureMockMvc
class SupplyChainContractTest extends IntegrationTestBase {

    private static final String API = "/api/v1/supply";
    private static final int OTHER_TENANT = 99000096;
    private static final long ACCOUNT = 99000002L;
    private static final int MENU_COVERAGE = 110237;
    private static final int BTN_SUPPLIER_VIEW = 110174;
    private static final String MODEL_KEY = "ITSC001";

    @Autowired private MockMvc mvc;
    @Autowired private JwtUtils jwtUtils;
    @Autowired private StringRedisTemplate redis;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private SupplyDailyJob job;

    private long brandId;
    private long supplierId;
    private long firstItemId;

    @BeforeEach
    void data() {
        cleanup();
        jdbc.update("insert into product_brand (tenant_id, brand_name, status) values (0, 'ITSCBRAND', 1)");
        brandId = jdbc.queryForObject("select id from product_brand where brand_name = 'ITSCBRAND'", Long.class);
        jdbc.update("insert into supplier (tenant_id, supplier_code, name, status) values (0, 'ITSC-SUP', 'IT供应商甲', 1)");
        supplierId = jdbc.queryForObject("select id from supplier where supplier_code = 'ITSC-SUP'", Long.class);
        // 近 90 天 3 次询盘 → 高优先级，进入覆盖矩阵
        for (int i = 1; i <= 3; i++) {
            jdbc.update("insert into customer_inquiry (tenant_id, inquiry_code, status, inquiry_date, quote_deadline)"
                    + " values (0, ?, 2, curdate(), curdate())", "ITSC-INQ-" + i);
            long inq = jdbc.queryForObject("select id from customer_inquiry where inquiry_code = ?", Long.class, "ITSC-INQ-" + i);
            jdbc.update("insert into inquiry_item (tenant_id, customer_inquiry_id, line_no, brand, brand_id, original_model,"
                    + " confirmed_model, model_key, quantity) values (0, ?, 1, 'ITSCBRAND', ?, 'ITSC-001', 'ITSC-001', ?, 2)",
                    inq, brandId, MODEL_KEY);
            if (i == 1) {
                firstItemId = jdbc.queryForObject("select id from inquiry_item where customer_inquiry_id = ?", Long.class, inq);
            }
        }
    }

    @AfterEach
    void cleanup() {
        for (String t : List.of("sc_gap_project_item", "sc_gap_project", "sc_followup_log", "sc_coverage_channel",
                "sc_demand_signal", "sc_demand")) {
            jdbc.update("delete from " + t + " where tenant_id in (0, ?)", OTHER_TENANT);
        }
        jdbc.update("delete from document_log where tenant_id = 0 and doc_type in ('SC', 'GP')");
        jdbc.update("delete from sourcing_quote where task_id in (select id from sourcing_task where task_code = 'ITSC-T1')");
        jdbc.update("delete from sourcing_task where task_code = 'ITSC-T1'");
        jdbc.update("delete from inquiry_item where customer_inquiry_id in"
                + " (select id from customer_inquiry where inquiry_code like 'ITSC-INQ-%')");
        jdbc.update("delete from customer_inquiry where inquiry_code like 'ITSC-INQ-%'");
        jdbc.update("delete from supplier where supplier_code = 'ITSC-SUP'");
        jdbc.update("delete from product_brand where brand_name = 'ITSCBRAND'");
    }

    @Test
    void importStockoutRecordAndProject() throws Exception {
        runJob();
        Map<String, Object> d = demand();
        long id = ((Number) d.get("id")).longValue();
        assertEquals(3, ((Number) d.get("cnt_inquiry")).intValue());
        assertEquals(2, ((Number) d.get("status")).intValue(), "高优先级自动进入覆盖矩阵");
        assertEquals(1, ((Number) d.get("coverage_status")).intValue(), "没有渠道与证据，建议「无渠道」");
        runJob();
        assertEquals(3, ((Number) demand().get("cnt_inquiry")).intValue(), "重复执行不重复计数");

        // 采购回价无货 → 第二天定时任务汇入并提醒
        jdbc.update("insert into sourcing_task (tenant_id, task_code) values (0, 'ITSC-T1')");
        long task = jdbc.queryForObject("select id from sourcing_task where task_code = 'ITSC-T1'", Long.class);
        jdbc.update("insert into sourcing_quote (tenant_id, task_id, inquiry_item_id, brand, model, model_key, no_stock, note,"
                + " status, quoted_at) values (0, ?, ?, 'ITSCBRAND', 'ITSC-001', ?, 1, '原厂停产', 2, now())", task, firstItemId, MODEL_KEY);
        runJob();
        d = demand();
        assertEquals(1, ((Number) d.get("cnt_stockout")).intValue());
        assertEquals(1, ((Number) d.get("reminder_type")).intValue(), "无货提醒");
        assertTrue(((String) d.get("next_action")).startsWith("询价无货 · 原厂停产"), String.valueOf(d.get("next_action")));

        loginAsAdmin("it_sc_admin");
        String admin = token("it_sc_admin");
        jdbc.update("update sc_demand set owner_id = ? where id = ?", ACCOUNT, id);
        JsonNode today = ok(call(get(API + "/today"), admin));
        assertEquals(1, today.path("dueToday").asInt(), "负责人今日待办里有无货提醒：" + today);

        // 记一次处理：改为需确认、定下一步 → 提醒清掉，下一步为人定
        Map<String, Object> rec = new LinkedHashMap<>();
        rec.put("demandIds", List.of(id));
        rec.put("content", "找到替代渠道，待确认价格");
        rec.put("coverage", 3);
        rec.put("nextAction", "下周回访");
        rec.put("nextActionDate", LocalDate.now().plusDays(7).toString());
        rec.put("idempotencyKey", "itsc-rec-1");
        JsonNode r = ok(call(json(post(API + "/records"), write(rec)), admin));
        assertEquals(1, r.path("changed").asInt(), r.toString());
        assertTrue(ok(call(json(post(API + "/records"), write(rec)), admin)).path("duplicate").asBoolean());
        assertEquals(1, jdbc.queryForObject("select count(*) from sc_followup_log where tenant_id = 0 and record_type = 1",
                Integer.class), "幂等键防重复提交");
        d = demand();
        assertEquals(3, ((Number) d.get("coverage_status")).intValue());
        assertEquals(1, ((Number) d.get("next_action_source")).intValue(), "下一步为人定");
        assertEquals(0, ((Number) d.get("reminder_type")).intValue());

        // 专项：带入该型号，需确认算已解决
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("brandId", brandId);
        p.put("name", "ITSC 补渠道");
        p.put("goal", "找到两家可报价渠道");
        p.put("ownerId", ACCOUNT);
        p.put("dueDate", LocalDate.now().plusDays(14).toString());
        p.put("demandIds", List.of(id));
        long project = ok(call(json(post(API + "/projects"), write(p)), admin)).asLong();
        JsonNode pv = ok(call(get(API + "/projects/" + project), admin));
        assertEquals(1, pv.path("solved").asInt());
        assertEquals(1, pv.path("total").asInt());
        assertTrue(pv.path("records").toString().contains("找到替代渠道"), "专项里看得到处理记录");
        assertTrue(call(json(post(API + "/projects"), write(p)), admin).path("code").asInt() != 0, "同范围只能有一个进行中的专项");
    }

    @Test
    void supplierHiddenWithoutPermission_andTenantIsolated() throws Exception {
        runJob();
        long id = ((Number) demand().get("id")).longValue();
        jdbc.update("insert into sc_coverage_channel (tenant_id, demand_id, supplier_id, role, sort_order) values (0, ?, ?, 1, 1)",
                id, supplierId);

        loginWithResources("it_sc_viewer", MENU_COVERAGE);
        String viewer = token("it_sc_viewer");
        JsonNode rows = ok(call(get(API + "/matrix/models").param("brandId", String.valueOf(brandId)), viewer)).path("records");
        assertEquals(1, rows.size(), rows.toString());
        assertTrue(rows.get(0).path("primarySupplier").isMissingNode() || rows.get(0).path("primarySupplier").isNull(),
                "没有「查看货源信息」不返回供应商");
        assertEquals(403, perform(json(post(API + "/records"), "{\"demandIds\":[" + id + "],\"content\":\"x\"}"), viewer)
                .getStatus(), "只有覆盖矩阵菜单不能记处理");

        loginWithResources("it_sc_viewer", MENU_COVERAGE, BTN_SUPPLIER_VIEW);
        String buyer = token("it_sc_viewer");
        rows = ok(call(get(API + "/matrix/models").param("brandId", String.valueOf(brandId)), buyer)).path("records");
        assertEquals("IT供应商甲", rows.get(0).path("primarySupplier").asText());

        // 另一个租户的需求对象看不到
        jdbc.update("insert into sc_demand (tenant_id, brand_id, model_key, model, scope_key, status) values (?, ?, 'ITSC002',"
                + " 'ITSC-002', 'm:ITSC002', 2)", OTHER_TENANT, brandId);
        long other = jdbc.queryForObject("select id from sc_demand where tenant_id = ?", Long.class, OTHER_TENANT);
        assertNotEquals(0, call(get(API + "/demands/" + other), buyer).path("code").asInt(), "跨租户读取被拒");
    }

    @Test
    void matrixModelsFilters() throws Exception {
        runJob();
        // 同品牌再放一个观察中、一个暂不覆盖的需求对象
        jdbc.update("insert into sc_demand (tenant_id, brand_id, model_key, model, scope_key, status, priority) values"
                + " (0, ?, 'ITSCW01', 'ITSC-W01', 'm:ITSCW01', 1, 1), (0, ?, 'ITSCP01', 'ITSC-P01', 'm:ITSCP01', 3, 1)",
                brandId, brandId);
        loginWithResources("it_sc_viewer", MENU_COVERAGE);
        String viewer = token("it_sc_viewer");
        String brand = String.valueOf(brandId);

        JsonNode rows = ok(call(get(API + "/matrix/models").param("brandId", brand), viewer)).path("records");
        assertEquals(List.of("ITSC-001"), models(rows), "默认只看已进入矩阵的");
        rows = ok(call(get(API + "/matrix/models").param("brandId", brand).param("all", "true"), viewer)).path("records");
        assertEquals(2, rows.size(), "all 含观察中，不含暂不覆盖：" + rows);
        assertTrue(models(rows).containsAll(List.of("ITSC-001", "ITSC-W01")), rows.toString());
        rows = ok(call(get(API + "/matrix/models").param("brandId", brand).param("all", "true")
                .param("keyword", " w01 "), viewer)).path("records");
        assertEquals(List.of("ITSC-W01"), models(rows), "关键词按型号模糊匹配，去掉首尾空格、不分大小写");
        rows = ok(call(get(API + "/matrix/models").param("brandId", brand).param("priority", "1"), viewer)).path("records");
        assertEquals(0, rows.size(), "按优先级筛选：矩阵内型号是高优先级");
    }

    private static List<String> models(JsonNode rows) {
        List<String> out = new java.util.ArrayList<>();
        rows.forEach(r -> out.add(r.path("model").asText()));
        return out;
    }

    private void runJob() {
        TenantContext.runAs(0, () -> job.runTenant(0, LocalDate.now()));
    }

    private Map<String, Object> demand() {
        return jdbc.queryForMap("select * from sc_demand where tenant_id = 0 and brand_id = ? and model_key = ?", brandId, MODEL_KEY);
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
