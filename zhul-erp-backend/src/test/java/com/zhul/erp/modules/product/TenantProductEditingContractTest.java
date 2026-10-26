package com.zhul.erp.modules.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.product.constants.ProductConstants;
import com.zhul.erp.modules.product.dto.SaveProductRequest;
import com.zhul.erp.modules.product.service.ProductService;
import com.zhul.erp.modules.product.support.PlatformScopeGuard;
import com.zhul.erp.support.TenantContractSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * spec product/product「商品数据平台共享，按归属维护」「商品资料完整度（平台与租户可见）」「按缺项筛选商品（平台与租户）」：
 * 租户按按钮权限维护本租户建的商品，不能改平台或其他租户的商品；新建时品牌、品类须属于本行业；候选审核建档属于该租户；
 * 租户看得到完整度，缺项筛选与统计只含本行业。
 */
class TenantProductEditingContractTest extends TenantContractSupport {

    private static final String IC = "INDUSTRIAL_CONTROL";
    private static final String MED = "IT_TP_MEDICAL";
    private static final int TENANT = 99000501;
    private static final int OTHER = 99000502;
    private static final String BOSS = "it_tp_boss";
    private static final String CLERK = "it_tp_clerk";

    @Autowired private ProductService productService;
    @Autowired private PlatformScopeGuard platformScopeGuard;

    private long icSub;
    private long medSub;
    private long brandIc;
    private long brandMed;
    private long platformProduct;
    private long otherProduct;
    private long medProduct;

    @BeforeEach
    void setUp() {
        cleanup();
        jdbc.update("insert into dict_item (tenant_id, dict_type_id, dict_type, item_code, item_name, item_value, sort_order, status) "
                + "select 0, id, 'industry', ?, '医疗（测试）', ?, 98, 1 from dict_type where tenant_id = 0 and dict_type = 'industry'", MED, MED);
        jdbc.update("insert into tenant (id, code, name, industry_code) values (?, 'ITTP1', 'IT 商品维护租户', ?)", TENANT, IC);
        jdbc.update("insert into tenant (id, code, name, industry_code) values (?, 'ITTP2', 'IT 其他租户', ?)", OTHER, IC);
        long icRoot = category("ittp_ic", null, IC);
        icSub = category("ittp_ic_sub", icRoot, "");
        long medRoot = category("ittp_med", null, MED);
        medSub = category("ittp_med_sub", medRoot, "");
        brandIc = brand("ITTP-IC", IC);
        brandMed = brand("ITTP-MED", MED);
        platformProduct = product("ITTPX-PLAT", brandIc, icSub, 0);
        otherProduct = product("ITTPX-OTHER", brandIc, icSub, OTHER);
        medProduct = product("ITTPX-MED", brandMed, medSub, 0);
        redis.delete(List.of(ProductConstants.CACHE_KEY_BRAND_OPTIONS, ProductConstants.CACHE_KEY_CATEGORY_OPTIONS));
        // 有按钮权限的负责人（管理员标记放行权限码，归属规则照常生效）
        loginAsAdmin(BOSS);
    }

    @AfterEach
    void cleanup() {
        TenantContext.clear();
        jdbc.update("delete from product_logistics where product_id in (select id from product where mpn_raw like 'ITTPX-%')");
        jdbc.update("delete from product where mpn_raw like 'ITTPX-%'");
        jdbc.update("delete from product_brand_industry where brand_id in (select id from product_brand where brand_name like 'ITTP-%')");
        jdbc.update("delete from product_brand where brand_name like 'ITTP-%'");
        jdbc.update("delete from product_category where category_code like 'ittp\\_%'");
        jdbc.update("delete from tenant where id in (?, ?)", TENANT, OTHER);
        jdbc.update("delete from dict_item where dict_type = 'industry' and item_code = ?", MED);
        redis.delete(List.of(ProductConstants.CACHE_KEY_BRAND_OPTIONS, ProductConstants.CACHE_KEY_CATEGORY_OPTIONS));
    }

    private long category(String code, Long parentId, String industry) {
        jdbc.update("insert into product_category (tenant_id, category_code, category_name, category_name_zh, parent_id, industry_code, status) "
                + "values (0, ?, ?, ?, ?, ?, 1)", code, code, code, parentId, industry);
        return jdbc.queryForObject("select id from product_category where category_code = ?", Long.class, code);
    }

    private long brand(String name, String industry) {
        jdbc.update("insert into product_brand (tenant_id, brand_name, status) values (0, ?, 1)", name);
        long id = jdbc.queryForObject("select id from product_brand where brand_name = ?", Long.class, name);
        jdbc.update("insert into product_brand_industry (brand_id, industry_code) values (?, ?)", id, industry);
        return id;
    }

    private long product(String mpn, long brandId, long categoryId, int owner) {
        jdbc.update("insert into product (tenant_id, owner_tenant_id, brand_id, category_id, mpn_raw, mpn_normalized, product_name, status) "
                + "values (0, ?, ?, ?, ?, ?, ?, 1)", owner, brandId, categoryId, mpn, mpn.toLowerCase().replace("-", ""), mpn);
        return jdbc.queryForObject("select id from product where mpn_raw = ?", Long.class, mpn);
    }

    private String createBody(String mpn, long brandId, long categoryId) throws Exception {
        return write(Map.of("brandId", brandId, "categoryId", categoryId, "mpnRaw", mpn, "productName", mpn));
    }

    @Test
    void tenantMaintainsOwnProduct() throws Exception {
        String t = token(BOSS, TENANT);
        JsonNode created = ok(call(json(post("/api/v1/product/products"), createBody("ITTPX-MINE", brandIc, icSub)), t));
        long id = created.path("id").asLong();
        assertEquals(TENANT, created.path("ownerTenantId").asInt(), "租户新建的商品属于本租户");
        assertTrue(created.path("editable").asBoolean());

        ok(call(json(put("/api/v1/product/products/" + id), createBody("ITTPX-MINE", brandIc, icSub)
                .replace("\"productName\":\"ITTPX-MINE\"", "\"productName\":\"本公司商品\"")), t));
        ok(call(json(put("/api/v1/product/products/" + id + "/logistics"), "{\"netWeightKg\":1.5}"), t));
        ok(call(json(patch("/api/v1/product/products/" + id + "/status"), "{\"status\":0}"), t));
        ok(call(delete("/api/v1/product/products/" + id), t));
        ok(call(post("/api/v1/product/products/" + id + "/restore"), t));
        assertEquals("本公司商品", jdbc.queryForObject("select product_name from product where id = ?", String.class, id));

        JsonNode mine = ok(call(get("/api/v1/product/products").param("mine", "true").param("keyword", "ITTPX"), t));
        assertEquals(List.of("ITTPX-MINE"), mpns(mine.path("records")), "只看本公司维护");
    }

    @Test
    void tenantCannotWriteOthersProducts() throws Exception {
        String t = token(BOSS, TENANT);
        for (long id : new long[]{platformProduct, otherProduct}) {
            JsonNode res = fail(call(json(put("/api/v1/product/products/" + id + "/logistics"), "{\"netWeightKg\":1.5}"), t));
            assertEquals("该商品由其他公司维护，只能查看", res.path("message").asText());
            fail(call(json(patch("/api/v1/product/products/" + id + "/status"), "{\"status\":0}"), t));
            fail(call(delete("/api/v1/product/products/" + id), t));
        }
        JsonNode detail = ok(call(get("/api/v1/product/products/" + platformProduct), t));
        assertFalse(detail.path("editable").asBoolean(), "平台的商品对租户只读");
        assertEquals(1, jdbc.queryForObject("select status from product where id = ?", Integer.class, platformProduct));
    }

    @Test
    void tenantWithoutButtonPermissionIsRejected() throws Exception {
        loginWithResources(CLERK);
        fail(call(json(post("/api/v1/product/products"), createBody("ITTPX-CLERK", brandIc, icSub)), token(CLERK, TENANT)));
        assertEquals(0, jdbc.queryForObject("select count(*) from product where mpn_raw = 'ITTPX-CLERK'", Integer.class));
    }

    @Test
    void tenantCreateMustStayInOwnIndustry() throws Exception {
        JsonNode res = fail(call(json(post("/api/v1/product/products"), createBody("ITTPX-WRONG", brandIc, medSub)),
                token(BOSS, TENANT)));
        assertEquals("品牌或品类不属于本公司行业", res.path("message").asText());
    }

    @Test
    void candidateReviewProductBelongsToTenant() {
        TenantContext.setTenantId(TENANT);
        SaveProductRequest req = new SaveProductRequest();
        req.setBrandId(brandIc);
        req.setCategoryId(icSub);
        req.setMpnRaw("ITTPX-CAND");
        long id = platformScopeGuard.asCandidateReview(() -> productService.create(req)).getId();
        assertEquals(TENANT, jdbc.queryForObject("select owner_tenant_id from product where id = ?", Integer.class, id));
    }

    @Test
    void tenantSeesCompletenessWithinIndustry() throws Exception {
        String t = token(BOSS, TENANT);
        JsonNode list = ok(call(get("/api/v1/product/products").param("keyword", "ITTPX"), t));
        assertTrue(mpns(list.path("records")).containsAll(List.of("ITTPX-PLAT", "ITTPX-OTHER")));
        assertFalse(mpns(list.path("records")).contains("ITTPX-MED"), "其他行业的商品不在列表里");
        list.path("records").forEach(r -> assertFalse(r.path("completeness").isMissingNode() || r.path("completeness").isNull(),
                "租户看得到完整度"));

        JsonNode missing = ok(call(get("/api/v1/product/products").param("keyword", "ITTPX").param("missing", "logistics"), t));
        assertEquals(2, missing.path("total").asInt(), "缺项筛选对租户生效");
        long summary = ok(call(get("/api/v1/product/products/completeness-summary"), t)).path("missingLogistics").asLong();
        long platformSummary = ok(call(get("/api/v1/product/products/completeness-summary"), token(BOSS, 0))).path("missingLogistics").asLong();
        assertTrue(platformSummary > summary, "租户的缺项统计不含其他行业的商品（医疗商品只算在平台里）");
    }

    private static List<String> mpns(JsonNode records) {
        List<String> list = new ArrayList<>();
        records.forEach(r -> list.add(r.path("mpnRaw").asText()));
        return list;
    }
}
