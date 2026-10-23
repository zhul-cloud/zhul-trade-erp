package com.zhul.erp.modules.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.product.constants.ProductConstants;
import com.zhul.erp.modules.product.support.BrandResolver;
import com.zhul.erp.modules.product.support.CategoryMatcher;
import com.zhul.erp.modules.tenant.dto.SaveTenantRequest;
import com.zhul.erp.modules.tenant.dto.TenantCreateResultVO;
import com.zhul.erp.modules.tenant.service.TenantService;
import com.zhul.erp.support.TenantContractSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * spec product/industry：租户只读到本行业的品牌、品类、系列、商品（列表、选项、品类树、搜索与匹配、品牌识别、品类匹配），
 * 按 ID 的详情不受限；平台看全部并可按行业筛选；租户创建时的行业规则。
 */
class ProductIndustryScopeContractTest extends TenantContractSupport {

    private static final String IC = "INDUSTRIAL_CONTROL";
    private static final String MED = "IT_MEDICAL";
    private static final int TENANT_IC = 99000301;
    private static final int TENANT_MED = 99000302;
    private static final int TEST_PACKAGE_ID = 99000303;

    @Autowired private BrandResolver brandResolver;
    @Autowired private CategoryMatcher categoryMatcher;
    @Autowired private TenantService tenantService;

    private long icRoot;
    private long medRoot;
    private long medSub;
    private long brandIc;
    private long brandMed;
    private long brandBoth;
    private long productMed;

    @BeforeEach
    void setUp() {
        cleanup();
        jdbc.update("insert into dict_item (tenant_id, dict_type_id, dict_type, item_code, item_name, item_value, sort_order, status) "
                + "select 0, id, 'industry', ?, '医疗器械（测试）', ?, 99, 1 from dict_type where tenant_id = 0 and dict_type = 'industry'",
                MED, MED);
        jdbc.update("insert into tenant (id, code, name, industry_code) values (?, 'ITIND1', 'IT 工控租户', ?)", TENANT_IC, IC);
        jdbc.update("insert into tenant (id, code, name, industry_code) values (?, 'ITIND2', 'IT 医疗租户', ?)", TENANT_MED, MED);

        icRoot = category("itind_ic", null, IC);
        long icSub = category("itind_ic_sub", icRoot, "");
        medRoot = category("itind_med", null, MED);
        medSub = category("itind_med_sub", medRoot, "");
        brandIc = brand("ITIND-IC", IC);
        brandMed = brand("ITIND-MED", MED);
        brandBoth = brand("ITIND-BOTH", IC, MED);
        jdbc.update("insert into product_series (tenant_id, brand_id, series_name, status) values (0, ?, 'ITIND-MS', 1)", brandMed);
        jdbc.update("insert into product_series (tenant_id, brand_id, series_name, status) values (0, ?, 'ITIND-BS', 1)", brandBoth);
        product("ITINDX-100", brandIc, icSub);
        productMed = product("ITINDX-200", brandMed, medSub);
        evictOptionCaches();
    }

    /** 直接写库的品牌、品类不会触发选项缓存失效，这里手动清掉 */
    private void evictOptionCaches() {
        redis.delete(List.of(ProductConstants.CACHE_KEY_BRAND_OPTIONS, ProductConstants.CACHE_KEY_CATEGORY_OPTIONS));
    }

    @AfterEach
    void cleanup() {
        TenantContext.clear();
        jdbc.update("delete from product where mpn_raw like 'ITINDX-%'");
        jdbc.update("delete from product_series where series_name like 'ITIND-%'");
        jdbc.update("delete from product_brand_industry where brand_id in (select id from product_brand where brand_name like 'ITIND-%')");
        jdbc.update("delete from product_brand where brand_name like 'ITIND-%'");
        jdbc.update("delete from product_category where category_code like 'itind\\_%'");
        jdbc.update("delete from tenant where id in (?, ?)", TENANT_IC, TENANT_MED);
        jdbc.update("delete from dict_item where dict_type = 'industry' and item_code = ?", MED);
        jdbc.update("delete from account_local_auth where account_id in "
                + "(select id from account where tenant_id in (select id from tenant where package_id = ?))", TEST_PACKAGE_ID);
        jdbc.update("delete from account where tenant_id in (select id from tenant where package_id = ?)", TEST_PACKAGE_ID);
        jdbc.update("delete from user_basic where tenant_id in (select id from tenant where package_id = ?)", TEST_PACKAGE_ID);
        jdbc.update("delete from tenant where package_id = ?", TEST_PACKAGE_ID);
        jdbc.update("delete from tenant_package where id = ?", TEST_PACKAGE_ID);
        evictOptionCaches();
    }

    private long category(String code, Long parentId, String industry) {
        jdbc.update("insert into product_category (tenant_id, category_code, category_name, category_name_zh, parent_id, industry_code, status) "
                + "values (0, ?, ?, ?, ?, ?, 1)", code, code, code, parentId, industry);
        return jdbc.queryForObject("select id from product_category where category_code = ?", Long.class, code);
    }

    private long brand(String name, String... industries) {
        jdbc.update("insert into product_brand (tenant_id, brand_name, status) values (0, ?, 1)", name);
        long id = jdbc.queryForObject("select id from product_brand where brand_name = ?", Long.class, name);
        for (String industry : industries) {
            jdbc.update("insert into product_brand_industry (brand_id, industry_code) values (?, ?)", id, industry);
        }
        return id;
    }

    private long product(String mpn, long brandId, long categoryId) {
        jdbc.update("insert into product (tenant_id, brand_id, category_id, mpn_raw, mpn_normalized, product_name, status) "
                + "values (0, ?, ?, ?, ?, ?, 1)", brandId, categoryId, mpn, mpn.toLowerCase().replace("-", ""), mpn);
        return jdbc.queryForObject("select id from product where mpn_raw = ?", Long.class, mpn);
    }

    private static List<Long> ids(JsonNode array) {
        List<Long> ids = new ArrayList<>();
        array.forEach(n -> ids.add(n.path("id").asLong()));
        return ids;
    }

    private static List<String> texts(JsonNode array, String field) {
        List<String> values = new ArrayList<>();
        array.forEach(n -> values.add(field == null ? n.asText() : n.path(field).asText()));
        return values;
    }

    @Test
    void tenantSeesOnlyOwnIndustry() throws Exception {
        String t = token("it_ind_ic", TENANT_IC);

        List<Long> brands = ids(ok(call(get("/api/v1/product/brands/options"), t)));
        assertTrue(brands.contains(brandIc) && brands.contains(brandBoth), "本行业与跨行业品牌可见");
        assertFalse(brands.contains(brandMed), "其他行业的品牌不可见");

        List<Long> categories = ids(ok(call(get("/api/v1/product/categories/options").param("level", "0"), t)));
        assertTrue(categories.contains(icRoot));
        assertFalse(categories.contains(medRoot) || categories.contains(medSub), "其他行业的品类及细分品类不可见");
        assertFalse(ids(ok(call(get("/api/v1/product/categories/tree"), t))).contains(medRoot), "品类树不含其他行业");

        assertEquals(0, ok(call(get("/api/v1/product/series/options").param("brandId", String.valueOf(brandMed)), t)).size(),
                "其他行业品牌的系列不可见");
        assertTrue(texts(ok(call(get("/api/v1/product/series/options").param("brandId", String.valueOf(brandBoth)), t)),
                "seriesName").contains("ITIND-BS"), "跨行业品牌的系列可见");

        List<String> found = texts(ok(call(get("/api/v1/product/products/search").param("keyword", "ITINDX"), t)), "productName");
        assertTrue(found.contains("ITINDX-100"));
        assertFalse(found.contains("ITINDX-200"), "搜索不到其他行业的商品");
        JsonNode matched = ok(call(get("/api/v1/product/products/match").param("brand", "ITIND-MED").param("mpn", "ITINDX-200"), t));
        assertTrue(matched.path("exact").isMissingNode() || matched.path("exact").isNull(), "匹配不到其他行业的商品");

        List<String> listed = texts(ok(call(get("/api/v1/product/products").param("keyword", "ITINDX"), t)).path("records"), "mpnRaw");
        assertEquals(List.of("ITINDX-100"), listed, "商品列表只有本行业");
        assertEquals(productMed, ok(call(get("/api/v1/product/products/" + productMed), t)).path("id").asLong(),
                "按 ID 打开详情不受行业限制");

        TenantContext.setTenantId(TENANT_IC);
        assertNull(brandResolver.resolve("ITIND-MED"), "询盘等识别不到其他行业的品牌");
        assertEquals(brandBoth, brandResolver.resolve("itind-both"));
        assertTrue(categoryMatcher.enabledCategories().stream().noneMatch(c -> c.getId().equals(medSub)),
                "品类匹配的候选范围不含其他行业");
    }

    @Test
    void otherIndustryTenantSeesItsOwn() throws Exception {
        String t = token("it_ind_med", TENANT_MED);
        List<Long> brands = ids(ok(call(get("/api/v1/product/brands/options"), t)));
        assertTrue(brands.contains(brandMed) && brands.contains(brandBoth));
        assertFalse(brands.contains(brandIc));
    }

    @Test
    void platformSeesAllAndFiltersByIndustry() throws Exception {
        loginAsAdmin("it_ind_platform");
        String p = token("it_ind_platform", 0);
        List<Long> brands = ids(ok(call(get("/api/v1/product/brands/options"), p)));
        assertTrue(brands.containsAll(List.of(brandIc, brandMed, brandBoth)), "平台看到全部行业");

        JsonNode medPage = ok(call(get("/api/v1/product/brands").param("industryCode", MED).param("keyword", "ITIND-")
                .param("pageSize", "50"), p));
        List<Long> medBrands = ids(medPage.path("records"));
        assertTrue(medBrands.contains(brandMed) && medBrands.contains(brandBoth));
        assertFalse(medBrands.contains(brandIc), "按行业筛选");
        medPage.path("records").forEach(r -> {
            if (r.path("id").asLong() == brandBoth) {
                assertEquals(List.of(IC, MED), texts(r.path("industryCodes"), null), "列表带所属行业");
            }
        });
        List<Long> medTree = ids(ok(call(get("/api/v1/product/categories/tree").param("industryCode", MED), p)));
        assertTrue(medTree.contains(medRoot) && !medTree.contains(icRoot));
    }

    @Test
    void tenantIndustryRules() {
        TenantContext.setTenantId(0);
        jdbc.update("insert into tenant_package (id, name, menu_ids, remark, status) values (?, '行业测试套餐', '[]', '', 1)",
                TEST_PACKAGE_ID);
        SaveTenantRequest req = new SaveTenantRequest();
        req.setName("行业测试_" + System.nanoTime());
        req.setPackageId(TEST_PACKAGE_ID);
        req.setContactName("测试联系人");
        req.setContactPhone("13800002222");
        req.setContactEmail("it-ind-" + System.nanoTime() + "@example.com");
        req.setExpireDate(LocalDate.now().plusYears(1));

        TenantCreateResultVO created = tenantService.create(req);
        assertEquals(IC, created.getTenant().getIndustryCode(), "接口没传行业时用默认行业");

        req.setName("行业测试_" + System.nanoTime());
        req.setContactPhone("13800003333");
        req.setContactEmail("it-ind-" + System.nanoTime() + "@example.com");
        req.setIndustryCode("NO_SUCH_INDUSTRY");
        BizException e = assertThrows(BizException.class, () -> tenantService.create(req));
        assertEquals("行业不存在或已停用", e.getMessage());
    }
}
