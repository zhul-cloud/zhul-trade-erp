package com.zhul.erp.modules.masterdata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.common.constants.RedisKeyConstants;
import com.zhul.erp.common.utils.JwtUtils;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 供应商主营产品、品类两级、品牌别名、待确认品牌的接口契约（add-supplier-brand-category），连测试库执行真实 SQL。
 * 测试账号在平台租户（tenant 0），所以既能维护供应商，也能以管理员身份维护品牌主数据。
 */
@AutoConfigureMockMvc
class SupplierProductScopeContractTest extends IntegrationTestBase {

    private static final String SUP = "/api/v1/masterdata/suppliers";
    private static final String BRAND = "/api/v1/product/brands";
    private static final String PENDING = "/api/v1/masterdata/pending-brands";

    @Autowired private MockMvc mvc;
    @Autowired private JwtUtils jwtUtils;
    @Autowired private StringRedisTemplate redis;
    @Autowired private ObjectMapper objectMapper;

    @BeforeEach
    void clean() {
        cleanup();
    }

    @AfterEach
    void cleanAfter() {
        cleanup();
    }

    private void cleanup() {
        jdbc.update("delete from supplier_product_scope where tenant_id = 0");
        jdbc.update("delete from supplier where tenant_id = 0");
        jdbc.update("delete from product_brand_alias where brand_id in (select id from product_brand where brand_name like 'ScopeIT%')");
        jdbc.update("delete from product_brand where brand_name like 'ScopeIT%'");
        jdbc.update("delete from product_category where category_code like 'scopeit%' and parent_id is not null");
        jdbc.update("delete from product_category where category_code like 'scopeit%'");
        redis.delete(redis.keys("zhul:erp:list:0:*"));
    }

    private String token(String username) {
        String token = jwtUtils.generateToken(Map.of("tenantId", 0), username, 3600);
        redis.opsForValue().set(RedisKeyConstants.TOKEN_PREFIX + token, "1");
        return token;
    }

    private JsonNode call(MockHttpServletRequestBuilder request, String token) throws Exception {
        request.header("Authorization", "Bearer " + token);
        return objectMapper.readTree(mvc.perform(request).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private int status(MockHttpServletRequestBuilder request, String token) throws Exception {
        request.header("Authorization", "Bearer " + token);
        return mvc.perform(request).andReturn().getResponse().getStatus();
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder b, String body) {
        return b.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private long ok(JsonNode res) {
        assertEquals(0, res.path("code").asInt(), res.toString());
        return res.path("data").path("id").asLong();
    }

    private long brand(String admin, String name, String... aliases) throws Exception {
        StringBuilder a = new StringBuilder();
        for (String alias : aliases) {
            a.append(a.length() == 0 ? "" : ",").append('"').append(alias).append('"');
        }
        return ok(call(json(post(BRAND), "{\"brandName\":\"" + name + "\",\"aliases\":[" + a + "]}"), admin));
    }

    /**
     * 按需创建细分品类（挂在测试专用的一级品类下）。不依赖迁移写入的初始品类：商品契约测试每次会清空整张品类表。
     */
    private long category(String code) throws Exception {
        String admin = token("scope_admin");
        Long top = jdbc.query("select id from product_category where category_code = 'scopeit_top'",
                rs -> rs.next() ? rs.getLong(1) : null);
        if (top == null) {
            top = ok(call(json(post("/api/v1/product/categories"),
                    "{\"categoryCode\":\"scopeit_top\",\"categoryName\":\"ScopeIT Controllers\"}"), admin));
        }
        String full = "scopeit_" + code;
        Long existing = jdbc.query("select id from product_category where category_code = ?",
                rs -> rs.next() ? rs.getLong(1) : null, full);
        if (existing != null) {
            return existing;
        }
        String zh = switch (code) { case "plc" -> "PLC"; case "hmi" -> "HMI"; default -> "变频器"; };
        return ok(call(json(post("/api/v1/product/categories"), "{\"categoryCode\":\"" + full + "\",\"categoryName\":\""
                + full + "\",\"categoryNameZh\":\"" + zh + "\",\"parentId\":" + top + "}"), admin));
    }

    private long supplier(String admin, String code, String scopes) throws Exception {
        JsonNode res = call(json(post(SUP), "{\"supplierCode\":\"" + code + "\",\"name\":\"" + code
                + " Ltd\",\"supplierType\":1,\"force\":true,\"productScopes\":" + scopes + "}"), admin);
        assertEquals(0, res.path("code").asInt(), res.toString());
        return res.path("data").path("createdSupplier").path("id").asLong();
    }

    @Test
    void scopesResolveAliasKeepPendingAndFilterIncludesAllCategories() throws Exception {
        loginAsAdmin("scope_admin");
        String admin = token("scope_admin");
        long siemens = brand(admin, "ScopeIT Siemens", "ScopeIT 西门子");
        long abb = brand(admin, "ScopeIT ABB");
        long plc = category("plc");
        long hmi = category("hmi");
        long vfd = category("vfd");

        long a = supplier(admin, "SCA", "[{\"brandName\":\"scopeit 西门子\",\"categoryIds\":[" + plc + "," + hmi + "]},"
                + "{\"brandName\":\"ScopeIT Hengstler\"}]");
        long b = supplier(admin, "SCB", "[{\"brandId\":" + siemens + "}]");
        supplier(admin, "SCC", "[{\"brandId\":" + abb + ",\"categoryIds\":[" + vfd + "]}]");

        JsonNode detail = call(get(SUP + "/" + a), admin).path("data").path("productScopes");
        assertEquals(siemens, detail.get(0).path("brandId").asLong(), "别名应归一到正式品牌");
        assertEquals("PLC", detail.get(0).path("categories").get(0).path("name").asText());
        assertTrue(detail.get(1).path("pending").asBoolean());

        JsonNode page = call(get(SUP + "/page").param("brandId", String.valueOf(siemens)).param("categoryId", String.valueOf(plc)), admin)
                .path("data");
        List<Long> ids = new ArrayList<>();
        page.path("records").forEach(r -> ids.add(r.path("id").asLong()));
        assertEquals(2, ids.size(), page.toString());
        assertTrue(ids.containsAll(List.of(a, b)), "全部品类的供应商也应命中");

        JsonNode dup = call(json(post(SUP), "{\"supplierCode\":\"SCD\",\"name\":\"SCD\",\"force\":true,\"productScopes\":"
                + "[{\"brandId\":" + siemens + "},{\"brandName\":\"ScopeIT 西门子\"}]}"), admin);
        assertEquals("主营品牌重复：ScopeIT 西门子", dup.path("message").asText());

        JsonNode top = call(json(post(SUP), "{\"supplierCode\":\"SCE\",\"name\":\"SCE\",\"force\":true,\"productScopes\":"
                + "[{\"brandId\":" + siemens + ",\"categoryIds\":[" + jdbc.queryForObject(
                "select parent_id from product_category where id = ?", Long.class, plc) + "]}]}"), admin);
        assertEquals("主营品类只能选择细分品类", top.path("message").asText());
    }

    @Test
    void quickCreateFromChannelTakesBrandNames() throws Exception {
        loginAsAdmin("scope_admin");
        String admin = token("scope_admin");
        long siemens = brand(admin, "ScopeIT Siemens", "ScopeIT 西门子");

        JsonNode res = call(json(post(SUP + "/from-channel"),
                "{\"channelName\":\"ScopeIT 五金店\",\"brandNames\":[\"ScopeIT 西门子\",\"ScopeIT Pilz\"]}"), admin);
        long id = res.path("data").path("createdSupplier").path("id").asLong();

        JsonNode scopes = call(get(SUP + "/" + id), admin).path("data").path("productScopes");
        assertEquals(siemens, scopes.get(0).path("brandId").asLong());
        assertEquals(0, scopes.get(0).path("categories").size());
        assertTrue(scopes.get(1).path("pending").asBoolean());
        assertEquals("", jdbc.queryForObject("select main_brands from supplier where id = ?", String.class, id),
                "不再写入旧的自由文本主营品牌");
    }

    @Test
    void pendingBrandLinkedAsAliasMergesIntoOfficialBrand() throws Exception {
        loginAsAdmin("scope_admin");
        String admin = token("scope_admin");
        long siemens = brand(admin, "ScopeIT Siemens");
        long plc = category("plc");
        long a = supplier(admin, "SPA", "[{\"brandId\":" + siemens + ",\"categoryIds\":[" + plc + "]},{\"brandName\":\"ScopeIT 西门子PLC\"}]");
        supplier(admin, "SPB", "[{\"brandName\":\"scopeit 西门子plc\"}]");

        JsonNode pending = call(get(PENDING), admin).path("data");
        JsonNode item = null;
        for (JsonNode p : pending) {
            if (p.path("pendingKey").asText().equals("scopeit 西门子plc")) {
                item = p;
            }
        }
        assertEquals(2, item.path("supplierCount").asInt(), pending.toString());
        assertFalse(item.has("tenantId"), "汇总不应暴露租户");

        JsonNode res = call(json(post(PENDING + "/alias"), "{\"pendingKey\":\"scopeit 西门子plc\",\"brandId\":" + siemens + "}"), admin);
        assertEquals(0, res.path("code").asInt(), res.toString());

        JsonNode scopes = call(get(SUP + "/" + a), admin).path("data").path("productScopes");
        assertEquals(1, scopes.size(), "同品牌应合并为一条：" + scopes);
        assertEquals(0, scopes.get(0).path("categories").size(), "任一方为全部品类，合并后为全部品类");
        JsonNode brand = call(get(BRAND).param("keyword", "ScopeIT 西门子PLC"), admin).path("data").path("records");
        assertEquals(siemens, brand.get(0).path("id").asLong(), "待确认名称成为别名，可按别名搜到品牌");
    }

    @Test
    void deletedSupplierNoLongerCountsAsReference() throws Exception {
        loginAsAdmin("scope_admin");
        String admin = token("scope_admin");
        long siemens = brand(admin, "ScopeIT Siemens");
        long plc = category("plc");
        long id = supplier(admin, "SDA", "[{\"brandId\":" + siemens + ",\"categoryIds\":[" + plc + "]},"
                + "{\"brandName\":\"ScopeIT Gone\"}]");
        assertEquals(0, call(delete(SUP + "/" + id), admin).path("code").asInt());

        JsonNode pending = call(get(PENDING), admin).path("data");
        pending.forEach(p -> assertFalse(p.path("pendingKey").asText().equals("scopeit gone"), "已删除供应商的待确认品牌不应出现"));
        assertEquals(0, call(delete("/api/v1/product/categories/" + plc), admin).path("code").asInt(), "只被已删除供应商使用的品类可以删除");
        assertEquals(0, call(delete(BRAND + "/" + siemens), admin).path("code").asInt(), "只被已删除供应商使用的品牌可以删除");
    }

    @Test
    void pendingBrandsRequireBrandPermission() throws Exception {
        loginWithResources("scope_staff");
        assertEquals(403, status(get(PENDING), token("scope_staff")));
    }

    @Test
    void categoryUsedBySupplierCannotBeDeletedAndProductsCannotUseSubCategory() throws Exception {
        loginAsAdmin("scope_admin");
        String admin = token("scope_admin");
        long siemens = brand(admin, "ScopeIT Siemens");
        long plc = category("plc");
        supplier(admin, "SPC", "[{\"brandId\":" + siemens + ",\"categoryIds\":[" + plc + "]}]");

        assertEquals("该品类已被供应商主营产品使用，可改为停用",
                call(delete("/api/v1/product/categories/" + plc), admin).path("message").asText());
        assertEquals("该品牌已被供应商主营产品使用，可改为停用",
                call(delete(BRAND + "/" + siemens), admin).path("message").asText());

        JsonNode tree = call(get("/api/v1/product/categories/tree"), admin).path("data");
        boolean found = false;
        for (JsonNode root : tree) {
            for (JsonNode child : root.path("children")) {
                found |= child.path("id").asLong() == plc;
            }
        }
        assertTrue(found, "细分品类应出现在树形接口的一级品类下");

        JsonNode product = call(json(post("/api/v1/product/products"), "{\"brandId\":" + siemens + ",\"categoryId\":" + plc
                + ",\"mpnRaw\":\"SCOPE-IT-1\"}"), admin);
        assertEquals("商品只能选择一级品类", product.path("message").asText());
    }
}
