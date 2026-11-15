package com.zhul.erp.modules.product.refine;

import com.zhul.erp.modules.aitask.support.AiTaskKeys;
import com.fasterxml.jackson.databind.JsonNode;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.aitask.dto.AiTaskCallbackRequest;
import com.zhul.erp.modules.aitask.service.AiOrchestratorClient;
import com.zhul.erp.modules.aitask.service.AiTaskService;
import com.zhul.erp.modules.product.constants.ProductConstants;
import com.zhul.erp.modules.supply.support.DemandCategorySync;
import com.zhul.erp.support.TenantContractSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * spec product/category-refine「按品类未细分筛选商品」「批量 AI 细分品类」「确认 AI 建议」，
 * 以及 supply-chain/demand-pool「需求型号品类按商品主数据补齐」。
 */
class CategoryRefineContractTest extends TenantContractSupport {

    private static final String IC = "INDUSTRIAL_CONTROL";
    private static final int TENANT = 99000601;
    private static final String BOSS = "it_cr_boss";
    private static final String PRODUCTS = "/api/v1/product/products";
    private static final String REFINE = "/api/v1/product/category-refine";

    @Autowired private AiTaskService aiTaskService;
    @Autowired private DemandCategorySync categorySync;
    @Autowired private TransactionTemplate tx;
    /** 不连真实 AI 编排服务：提交后任务停在排队中，由测试手动回调 */
    @MockBean private AiOrchestratorClient orchestrator;

    private long plc;
    private long cpu;
    private long io;
    private long hmi;
    private long touch;
    private long brand;
    private long otherBrand;
    private String t;

    @BeforeEach
    void setUp() {
        cleanup();
        seedAiConfig(TENANT);
        when(orchestrator.submit(any(), anyLong(), any(), any())).thenAnswer(inv -> new CompletableFuture<>());
        jdbc.update("insert into tenant_package (id, name, menu_ids) select ?, 'IT品类细分套餐', JSON_ARRAYAGG(id) from resource", TENANT);
        jdbc.update("insert into tenant (id, code, name, industry_code, package_id) values (?, 'ITCR1', 'IT 品类细分租户', ?, ?)",
                TENANT, IC, TENANT);
        plc = category("itcr_plc", null, IC, 1);
        cpu = category("itcr_cpu", plc, "", 1);
        io = category("itcr_io", plc, "", 1);
        category("itcr_off", plc, "", 0);
        hmi = category("itcr_hmi", null, IC, 1);
        touch = category("itcr_touch", hmi, "", 1);
        brand = brand("ITCR-SIE");
        otherBrand = brand("ITCR-DEL");
        redis.delete(List.of(ProductConstants.CACHE_KEY_BRAND_OPTIONS, ProductConstants.CACHE_KEY_CATEGORY_OPTIONS));
        // 账号建在本租户下，「同一账号只能跑一个」按当前用户判断
        testUserTenant = TENANT;
        loginAsAdmin(BOSS);
        t = token(BOSS, TENANT);
    }

    @AfterEach
    void cleanup() {
        TenantContext.clear();
        jdbc.update("delete from ai_task where skill_id = 'product-category-refine'");
        jdbc.update("delete from document_log where doc_type = 'SC' and doc_id in (select id from sc_demand where tenant_id = ?)", TENANT);
        jdbc.update("delete from sc_demand where tenant_id = ?", TENANT);
        jdbc.update("delete from product where mpn_raw like 'ITCRX-%'");
        jdbc.update("delete from product_brand_industry where brand_id in (select id from product_brand where brand_name like 'ITCR-%')");
        jdbc.update("delete from product_brand where brand_name like 'ITCR-%'");
        jdbc.update("delete from product_category where category_code like 'itcr\\_%'");
        jdbc.update("delete from tenant where id = ?", TENANT);
        jdbc.update("delete from tenant_package where id = ?", TENANT);
        removeAiConfig(TENANT);
        redis.delete(List.of(ProductConstants.CACHE_KEY_BRAND_OPTIONS, ProductConstants.CACHE_KEY_CATEGORY_OPTIONS));
    }

    private long category(String code, Long parentId, String industry, int status) {
        jdbc.update("insert into product_category (tenant_id, category_code, category_name, category_name_zh, parent_id, industry_code, status) "
                + "values (0, ?, ?, ?, ?, ?, ?)", code, code, "仝" + code, parentId, industry, status);
        return jdbc.queryForObject("select id from product_category where category_code = ?", Long.class, code);
    }

    private long brand(String name) {
        jdbc.update("insert into product_brand (tenant_id, brand_name, status) values (0, ?, 1)", name);
        long id = jdbc.queryForObject("select id from product_brand where brand_name = ?", Long.class, name);
        jdbc.update("insert into product_brand_industry (brand_id, industry_code) values (?, ?)", id, IC);
        return id;
    }

    private long product(String mpn, long brandId, long categoryId, int owner) {
        jdbc.update("insert into product (tenant_id, owner_tenant_id, brand_id, category_id, mpn_raw, mpn_normalized, product_name, status) "
                + "values (0, ?, ?, ?, ?, ?, ?, 1)", owner, brandId, categoryId, mpn, key(mpn), mpn);
        return jdbc.queryForObject("select id from product where mpn_raw = ?", Long.class, mpn);
    }

    private static String key(String mpn) {
        return mpn.toLowerCase().replace("-", "");
    }

    private void suggest(long product, long category) {
        jdbc.update("update product set ai_category_id = ?, ai_category_state = 1, ai_category_at = now() where id = ?", category, product);
    }

    private Long col(String column, long id) {
        return jdbc.queryForObject("select " + column + " from product where id = ?", Long.class, id);
    }

    private static List<String> mpns(JsonNode records) {
        List<String> out = new ArrayList<>();
        records.forEach(r -> out.add(r.path("mpnRaw").asText()));
        return out;
    }

    private JsonNode list(String state) throws Exception {
        var req = get(PRODUCTS).param("keyword", "ITCRX").param("missing", "refine").param("pageSize", "50");
        if (state != null) {
            req.param("refineState", state);
        }
        return ok(call(req, t));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> sentInput() throws Exception {
        ArgumentCaptor<String> input = ArgumentCaptor.forClass(String.class);
        verify(orchestrator).submit(eq("product-category-refine"), anyLong(), input.capture(), any());
        clearInvocations(orchestrator);
        return objectMapper.readValue(input.getValue(), Map.class);
    }

    private void callback(long task, List<Map<String, Object>> items) {
        AiTaskCallbackRequest cb = new AiTaskCallbackRequest();
        cb.setStatus("success");
        cb.setOutput(Map.of("items", items));
        TenantContext.runAs(TENANT, () -> aiTaskService.handleCallback(task, cb));
    }

    @Test
    void refineFilter_coversTopLevelOnly_andStatesAddUp() throws Exception {
        product("ITCRX-TODO", brand, plc, TENANT);
        long pending = product("ITCRX-PENDING", brand, hmi, TENANT);
        suggest(pending, touch);
        long ignored = product("ITCRX-IGNORED", brand, plc, TENANT);
        jdbc.update("update product set ai_category_state = 2, ai_category_at = now() where id = ?", ignored);
        product("ITCRX-DETAILED", brand, cpu, TENANT);

        assertEquals(List.of("ITCRX-IGNORED", "ITCRX-PENDING", "ITCRX-TODO"), mpns(list(null).path("records")).stream().sorted().toList(),
                "一级品类算未细分，细分品类不算");
        assertEquals(List.of("ITCRX-TODO"), mpns(list("todo").path("records")));
        assertEquals(List.of("ITCRX-IGNORED"), mpns(list("ignored").path("records")));
        JsonNode p = list("pending").path("records").get(0);
        assertEquals("ITCRX-PENDING", p.path("mpnRaw").asText());
        assertEquals(touch, p.path("aiCategoryId").asLong());
        assertEquals("仝itcr_touch", p.path("aiCategoryName").asText());
        assertFalse(p.path("aiCategoryCross").asBoolean(), "建议在当前一级品类下");
        fail(call(get(PRODUCTS).param("missing", "refine").param("refineState", "bad"), t));

        JsonNode s = ok(call(get(PRODUCTS + "/completeness-summary"), t));
        assertTrue(s.path("missingRefine").asLong() >= 3);
        assertEquals(s.path("missingRefine").asLong(),
                s.path("refineTodo").asLong() + s.path("refinePending").asLong() + s.path("refineIgnored").asLong(), "三档合计等于品类未细分");
        assertEquals(s.path("refineTodo").asLong(), ok(call(get(PRODUCTS).param("missing", "refine").param("refineState", "todo"), t))
                .path("total").asLong(), "数量与筛选结果一致");
    }

    @Test
    @SuppressWarnings("unchecked")
    void submit_onlyMaintainableTodo_resultIsSuggestionOnly() throws Exception {
        long a = product("ITCRX-A", brand, plc, TENANT);
        long b = product("ITCRX-B", brand, hmi, TENANT);
        long detailed = product("ITCRX-C", brand, cpu, TENANT);
        long platform = product("ITCRX-D", brand, plc, 0);
        long unknown = product("ITCRX-E", brand, plc, TENANT);

        JsonNode vo = ok(call(json(post(REFINE), write(Map.of("ids", List.of(a, b, detailed, platform, unknown)))), t));
        assertEquals(3, vo.path("total").asInt());
        assertEquals(2, vo.path("skipped").asInt(), "已细分、平台商品跳过");
        long task = vo.path("taskId").asLong();
        Map<String, Object> input = sentInput();
        List<Map<String, Object>> items = (List<Map<String, Object>>) input.get("items");
        assertEquals(List.of(AiTaskKeys.sign("p" + a + "#" + plc), AiTaskKeys.sign("p" + b + "#" + hmi),
                AiTaskKeys.sign("p" + unknown + "#" + plc)), items.stream().map(m -> m.get("key")).toList());
        assertEquals("仝itcr_plc", items.get(0).get("currentCategory"));
        List<Number> ids = ((List<Map<String, Object>>) input.get("categories")).stream().map(m -> (Number) m.get("id")).toList();
        assertTrue(ids.stream().map(Number::longValue).toList().containsAll(List.of(cpu, io, touch)));
        assertFalse(ids.stream().map(Number::longValue).toList().contains(plc), "只给细分品类");

        assertTrue(fail(call(json(post(REFINE), write(Map.of("ids", List.of(a)))), t)).path("message").asText().contains("还在运行"));

        // 运行期间 B 的品类被人改了
        jdbc.update("update product set category_id = ? where id = ?", plc, b);
        callback(task, List.of(
                Map.of("key", AiTaskKeys.sign("p" + a + "#" + plc), "categoryId", touch, "brand", ""),
                Map.of("key", AiTaskKeys.sign("p" + b + "#" + hmi), "categoryId", touch, "brand", ""),
                Map.of("key", AiTaskKeys.sign("p" + unknown + "#" + plc), "categoryId", 0, "brand", "")));

        assertEquals(plc, col("category_id", a), "AI 结果不改商品品类");
        assertEquals(touch, col("ai_category_id", a));
        assertEquals(1L, col("ai_category_state", a));
        assertEquals(0L, col("ai_category_state", b), "品类被改过的建议丢弃");
        assertEquals(0L, col("ai_category_state", unknown), "判断不出仍待 AI 细分");
        JsonNode status = ok(call(get(REFINE + "/" + task), t));
        assertEquals(1, status.path("suggested").asInt());
        assertEquals(1, status.path("unknown").asInt());
        assertEquals(1, status.path("discarded").asInt());
        JsonNode row = list("pending").path("records").get(0);
        assertTrue(row.path("aiCategoryCross").asBoolean(), "从 PLC 改到 HMI 的细分品类，跨一级品类");
    }

    @Test
    void submit_byFilter_takesTodoOnly() throws Exception {
        long a = product("ITCRX-F1", brand, plc, TENANT);
        long pending = product("ITCRX-F2", brand, plc, TENANT);
        suggest(pending, cpu);
        product("ITCRX-F3", otherBrand, plc, TENANT);

        JsonNode vo = ok(call(json(post(REFINE), write(Map.of("filter", Map.of("keyword", "ITCRX-F", "brandId", brand)))), t));
        assertEquals(1, vo.path("total").asInt(), "只取当前筛选里的待 AI 细分");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) sentInput().get("items");
        assertEquals(AiTaskKeys.sign("p" + a + "#" + plc), items.get(0).get("key"));
    }

    /** audit BUS-066：按筛选发起时不取不能维护的商品，判断不出的排到后面，下次轮到其余商品 */
    @Test
    @SuppressWarnings("unchecked")
    void submit_byFilter_skipsNotOwned_andMovesPastUnknown() throws Exception {
        long mineOld = product("ITCRX-W1", brand, plc, TENANT);
        long mineNew = product("ITCRX-W2", brand, plc, TENANT);
        long foreign = product("ITCRX-W3", brand, plc, TENANT + 1);
        long platform = product("ITCRX-W4", brand, plc, 0);
        jdbc.update("update product set update_time = now() - interval 3 day where id = ?", mineOld);
        jdbc.update("update product set update_time = now() - interval 2 day where id = ?", mineNew);
        jdbc.update("update product set update_time = now() - interval 1 day where id in (?, ?)", foreign, platform);
        Map<String, Object> filter = Map.of("filter", Map.of("keyword", "ITCRX-W"));

        // 第一次：只能拿到本公司维护的两个，其他公司、平台维护的不占名额
        JsonNode vo = ok(call(json(post(REFINE), write(filter)), t));
        assertEquals(2, vo.path("total").asInt(), "不能维护的商品不取");
        assertEquals(0, vo.path("skipped").asInt());
        List<Map<String, Object>> items = (List<Map<String, Object>>) sentInput().get("items");
        assertEquals(List.of(AiTaskKeys.sign("p" + mineNew + "#" + plc), AiTaskKeys.sign("p" + mineOld + "#" + plc)),
                items.stream().map(m -> m.get("key")).toList());
        // AI 两个都判断不出
        callback(vo.path("taskId").asLong(), List.of(
                Map.of("key", AiTaskKeys.sign("p" + mineNew + "#" + plc), "categoryId", 0, "brand", ""),
                Map.of("key", AiTaskKeys.sign("p" + mineOld + "#" + plc), "categoryId", 0, "brand", "")));
        assertEquals(0L, col("ai_category_state", mineNew), "判断不出仍待 AI 细分");

        // 再来一个没跑过的：下次先轮到它，判断不出的排在后面
        long fresh = product("ITCRX-W5", brand, plc, TENANT);
        jdbc.update("update product set update_time = now() - interval 5 day where id = ?", fresh);
        ok(call(json(post(REFINE), write(filter)), t));
        items = (List<Map<String, Object>>) sentInput().get("items");
        assertEquals(AiTaskKeys.sign("p" + fresh + "#" + plc), items.get(0).get("key"), "没跑过的排在判断不出的前面");
    }

    /** audit UI-084：任务查不到时带固定错误码，页面据此停止跟踪；其他错误（网络、5xx）继续轮询 */
    @Test
    void status_unknownTask_hasNotFoundCode() throws Exception {
        JsonNode res = fail(call(get(REFINE + "/" + Long.MAX_VALUE), t));
        assertEquals("AI_TASK_NOT_FOUND", res.path("data").path("errorCode").asText(), res.toString());
    }

    /** audit AI-011：模型抄错 key、或 key 指向其他公司维护的商品时，回调不写建议 */
    @Test
    void callback_dropsMiscopiedKeysAndOtherCompanyProducts() throws Exception {
        long a = product("ITCRX-K1", brand, plc, TENANT);
        long neighbour = product("ITCRX-K2", brand, plc, TENANT);
        long foreign = product("ITCRX-K3", brand, plc, TENANT + 1);
        long task = ok(call(json(post(REFINE), write(Map.of("ids", List.of(a)))), t)).path("taskId").asLong();
        String sent = AiTaskKeys.sign("p" + a + "#" + plc);
        callback(task, List.of(
                // 把 ID 抄成了同公司另一个待细分商品，校验码还是原来的
                Map.of("key", sent.replace("p" + a + "#", "p" + neighbour + "#"), "categoryId", cpu, "brand", ""),
                Map.of("key", "p" + neighbour + "#" + plc, "categoryId", cpu, "brand", ""),
                Map.of("key", AiTaskKeys.sign("p" + foreign + "#" + plc), "categoryId", cpu, "brand", ""),
                Map.of("key", sent, "categoryId", cpu, "brand", "")));

        assertEquals(1L, col("ai_category_state", a));
        assertEquals(cpu, col("ai_category_id", a));
        assertEquals(0L, col("ai_category_state", neighbour), "抄错或没有校验码的 key 丢弃");
        assertEquals(0L, col("ai_category_state", foreign), "其他公司维护的商品不写");
    }

    @Test
    void accept_ignore_unignore_withOwnershipAndChangedChecks() throws Exception {
        long a = product("ITCRX-G1", brand, plc, TENANT);
        suggest(a, cpu);
        long changed = product("ITCRX-G2", brand, plc, TENANT);
        suggest(changed, cpu);
        long platform = product("ITCRX-G3", brand, plc, 0);
        suggest(platform, cpu);
        long x = product("ITCRX-G4", brand, plc, TENANT);
        suggest(x, io);

        // 同事在商品详情改了品类：建议与忽略标记清除
        ok(call(json(put(PRODUCTS + "/" + changed), write(Map.of("brandId", brand, "categoryId", io, "mpnRaw", "ITCRX-G2",
                "productName", "ITCRX-G2"))), t));
        assertEquals(0L, col("ai_category_state", changed));
        assertEquals(0L, col("ai_category_id", changed));

        JsonNode r = ok(call(json(post(REFINE + "/accept"), write(Map.of("ids", List.of(a, changed, platform)))), t));
        assertEquals(1, r.path("done").asInt());
        assertEquals("品类已被修改", r.path("skipped").get(0).path("reason").asText());
        assertEquals("该商品由其他公司维护，只能查看", r.path("skipped").get(1).path("reason").asText());
        assertEquals(cpu, col("category_id", a));
        assertEquals(0L, col("ai_category_state", a));
        assertEquals(BOSS, jdbc.queryForObject("select update_by from product where id = ?", String.class, a));
        assertEquals(io, col("category_id", changed), "被改过的不覆盖");
        assertEquals(plc, col("category_id", platform));

        JsonNode ig = ok(call(json(post(REFINE + "/ignore"), write(Map.of("ids", List.of(x, a)))), t));
        assertEquals(1, ig.path("done").asInt());
        assertEquals("品类已细分", ig.path("skipped").get(0).path("reason").asText());
        assertEquals(2L, col("ai_category_state", x));
        assertEquals(0L, col("ai_category_id", x));
        assertEquals(List.of("ITCRX-G4"), mpns(list("ignored").path("records")));
        assertEquals(1, ok(call(json(post(REFINE + "/unignore"), write(Map.of("ids", List.of(x)))), t)).path("done").asInt());
        assertEquals(0L, col("ai_category_state", x));
        assertNull(jdbc.queryForObject("select ai_category_at from product where id = ?", Object.class, x));
    }

    @Test
    void supplyDemand_followsProductMaster() throws Exception {
        long a = product("ITCRX-S1", brand, plc, TENANT);
        suggest(a, cpu);
        long sameBrand = demand(brand, "ITCRX-S1", 0);
        long otherBrandDemand = demand(otherBrand, "ITCRX-S1", 0);
        long already = demand(brand, "ITCRX-S2", io);
        product("ITCRX-S2", brand, cpu, TENANT);
        long daily = demand(brand, "ITCRX-S3", 0);
        product("ITCRX-S3", brand, touch, TENANT);
        long stillTop = demand(brand, "ITCRX-S4", 0);
        product("ITCRX-S4", brand, plc, TENANT);

        // 采纳后当即补齐本公司的对应型号
        ok(call(json(post(REFINE + "/accept"), write(Map.of("ids", List.of(a)))), t));
        assertEquals(cpu, demandCategory(sameBrand));
        assertEquals(0L, demandCategory(otherBrandDemand), "品牌不同不补");
        assertEquals(0L, demandCategory(daily), "其他型号等每日任务");
        Map<String, Object> log = jdbc.queryForMap("select action, content, operator_id from document_log where doc_type = 'SC' and doc_id = ?", sameBrand);
        assertEquals("品类", log.get("action"));
        assertEquals("未分类 → 仝itcr_cpu（按商品主数据）", log.get("content"));
        assertEquals(0L, ((Number) log.get("operator_id")).longValue(), "操作人记为系统");

        // 每日任务兜底
        int[] filled = new int[1];
        TenantContext.runAs(TENANT, () -> filled[0] = tx.execute(st -> categorySync.fillAll()));
        assertEquals(1, filled[0]);
        assertEquals(touch, demandCategory(daily));
        assertEquals(io, demandCategory(already), "已有品类的不改");
        assertEquals(0L, demandCategory(stillTop), "商品仍是一级品类的保持未分类");
    }

    private long demand(long brandId, String model, long categoryId) {
        jdbc.update("insert into sc_demand (tenant_id, brand_id, category_id, model_key, model, scope_key) values (?, ?, ?, ?, ?, ?)",
                TENANT, brandId, categoryId, key(model), model, "m:" + key(model));
        return jdbc.queryForObject("select id from sc_demand where tenant_id = ? and brand_id = ? and model_key = ?", Long.class,
                TENANT, brandId, key(model));
    }

    private Long demandCategory(long id) {
        return jdbc.queryForObject("select category_id from sc_demand where id = ?", Long.class, id);
    }
}
