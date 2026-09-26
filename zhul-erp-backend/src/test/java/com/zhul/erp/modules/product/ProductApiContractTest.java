package com.zhul.erp.modules.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.common.constants.RedisKeyConstants;
import com.zhul.erp.common.utils.JwtUtils;
import com.zhul.erp.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * 商品接口契约：走完整的 HTTP + JWT + 权限链路，验证响应外形与状态码（任务 6.3）。
 * 成功：{"code":0,"data":...,"message":"ok"}；失败：数字 code + message，字符串错误码与 detail 在 data 里。
 */
@AutoConfigureMockMvc
class ProductApiContractTest extends IntegrationTestBase {

    private static final String BASE = "/api/v1/product";
    private static final byte[] PNG = java.util.Arrays.copyOf(
            new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}, 64);

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JwtUtils jwtUtils;
    @Autowired
    private StringRedisTemplate redis;
    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanData() {
        for (String table : List.of("product_reference_price", "product_customs", "product_logistics", "product_media",
                "product_faq", "product_application", "product_document", "product_relationship",
                "product_specification", "product", "product_series", "product_category", "product_brand")) {
            jdbc.update("delete from " + table);
        }
        redis.delete(redis.keys("zhul:erp:limit:product:upload:*"));
        redis.delete(redis.keys("zhul:erp:list:0:*"));
    }

    private String token(String username, int tenantId) {
        String token = jwtUtils.generateToken(Map.of("tenantId", tenantId), username, 3600);
        redis.opsForValue().set(RedisKeyConstants.TOKEN_PREFIX + token, "1");
        return token;
    }

    private JsonNode call(MockHttpServletRequestBuilder request, String token) throws Exception {
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        MvcResult result = mvc.perform(request).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private int statusOf(MockHttpServletRequestBuilder request, String token) throws Exception {
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return mvc.perform(request).andReturn().getResponse().getStatus();
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String body) {
        return builder.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private long createBrandAndCategoryAndProduct(String adminToken) throws Exception {
        long brand = call(json(post(BASE + "/brands"), "{\"brandName\":\"Siemens\"}"), adminToken).path("data").path("id").asLong();
        long category = call(json(post(BASE + "/categories"), "{\"categoryCode\":\"controllers\",\"categoryName\":\"Controllers\"}"),
                adminToken).path("data").path("id").asLong();
        return call(json(post(BASE + "/products"), "{\"brandId\":" + brand + ",\"categoryId\":" + category
                + ",\"mpnRaw\":\"6ES7 214-1BD23-0XB0\"}"), adminToken).path("data").path("id").asLong();
    }

    @Test
    void successResponsesUseCodeZeroOkAndData() throws Exception {
        loginAsAdmin("contract_admin");
        String admin = token("contract_admin", 0);

        JsonNode created = call(json(post(BASE + "/brands"), "{\"brandName\":\"Siemens\"}"), admin);

        assertEquals(0, created.path("code").asInt());
        assertEquals("ok", created.path("message").asText());
        assertTrue(created.path("data").path("id").asLong() > 0);
        assertEquals("Siemens", created.path("data").path("brandName").asText());
        assertEquals(1, created.path("data").path("status").asInt());
        assertFalse(created.path("data").has("tenantId"), "响应里不暴露 tenantId");
    }

    @Test
    void listResponsesCarryTotalAndRecordsAndAcceptPageParameters() throws Exception {
        loginAsAdmin("contract_admin");
        String admin = token("contract_admin", 0);
        createBrandAndCategoryAndProduct(admin);
        call(json(post(BASE + "/products"), "{\"brandId\":" + jdbc.queryForObject("select id from product_brand", Long.class)
                + ",\"categoryId\":" + jdbc.queryForObject("select id from product_category", Long.class)
                + ",\"mpnRaw\":\"OTHER-1\"}"), admin);

        JsonNode page = call(get(BASE + "/products").param("page", "1").param("pageSize", "1"), admin);

        assertEquals(0, page.path("code").asInt());
        assertEquals(2, page.path("data").path("total").asInt());
        assertEquals(1, page.path("data").path("records").size());
        JsonNode record = page.path("data").path("records").get(0);
        assertTrue(record.has("mpnDisplay"));
        assertTrue(record.has("brandName"));
        assertTrue(record.has("completeness"), "平台账号的列表带完整度");
        assertEquals(10, record.path("completeness").path("total").asInt());
    }

    @Test
    void errorResponsesUseNumericCodeMessageAndErrorCodeInData() throws Exception {
        loginAsAdmin("contract_admin");
        String admin = token("contract_admin", 0);
        long productId = createBrandAndCategoryAndProduct(admin);
        long brand = jdbc.queryForObject("select id from product_brand", Long.class);
        long category = jdbc.queryForObject("select id from product_category", Long.class);

        JsonNode duplicate = call(json(post(BASE + "/products"), "{\"brandId\":" + brand + ",\"categoryId\":" + category
                + ",\"mpnRaw\":\"6es7214-1bd23-0xb0\"}"), admin);

        assertEquals(500, duplicate.path("code").asInt());
        assertTrue(duplicate.path("code").isNumber());
        assertEquals("该型号已存在", duplicate.path("message").asText());
        assertEquals("PRODUCT_DUPLICATE", duplicate.path("data").path("errorCode").asText());
        assertEquals(productId, duplicate.path("data").path("detail").path("existingId").asLong());
        assertFalse(duplicate.path("data").path("detail").path("deleted").asBoolean(true));

        JsonNode notFound = call(get(BASE + "/products/987654"), admin);
        assertEquals(500, notFound.path("code").asInt());
        assertEquals("PRODUCT_NOT_FOUND", notFound.path("data").path("errorCode").asText());

        JsonNode invalid = call(json(post(BASE + "/products"), "{\"brandId\":" + brand + ",\"categoryId\":" + category
                + ",\"mpnRaw\":\"---\"}"), admin);
        assertEquals("PRODUCT_MPN_INVALID", invalid.path("data").path("errorCode").asText());
    }

    @Test
    void deletedModelIsReportedWithDeletedFlagOverHttp() throws Exception {
        loginAsAdmin("contract_admin");
        String admin = token("contract_admin", 0);
        long productId = createBrandAndCategoryAndProduct(admin);
        call(delete(BASE + "/products/" + productId), admin);
        long brand = jdbc.queryForObject("select id from product_brand", Long.class);
        long category = jdbc.queryForObject("select id from product_category", Long.class);

        JsonNode duplicate = call(json(post(BASE + "/products"), "{\"brandId\":" + brand + ",\"categoryId\":" + category
                + ",\"mpnRaw\":\"6ES7 214-1BD23-0XB0\"}"), admin);

        assertTrue(duplicate.path("data").path("detail").path("deleted").asBoolean());
        JsonNode restored = call(post(BASE + "/products/" + productId + "/restore"), admin);
        assertEquals(0, restored.path("code").asInt());
        assertEquals(productId, restored.path("data").path("id").asLong());
    }

    @Test
    void tenantTokenCanReadButWriteReturnsPlatformAdminRequired() throws Exception {
        loginAsAdmin("contract_admin");
        String platform = token("contract_admin", 0);
        long productId = createBrandAndCategoryAndProduct(platform);
        // 同一个有权限的账号，但令牌里的租户是 1001：读取正常，写入被平台账号校验拒绝
        String tenant = token("contract_admin", 1001);

        JsonNode read = call(get(BASE + "/products/" + productId), tenant);
        assertEquals(0, read.path("code").asInt());
        assertFalse(read.path("data").has("completeness"), "租户账号看不到完整度");
        assertEquals(0, call(get(BASE + "/products/search").param("keyword", "6ES7"), tenant).path("code").asInt());

        JsonNode write = call(json(post(BASE + "/brands"), "{\"brandName\":\"ABB\"}"), tenant);
        assertEquals(500, write.path("code").asInt());
        assertEquals("PLATFORM_ADMIN_REQUIRED", write.path("data").path("errorCode").asText());
        assertEquals("仅平台账号可维护商品主数据", write.path("message").asText());
        assertEquals(1, jdbc.queryForObject("select count(*) from product_brand", Integer.class));

        JsonNode summary = call(get(BASE + "/products/completeness-summary"), tenant);
        assertEquals("PLATFORM_ADMIN_REQUIRED", summary.path("data").path("errorCode").asText());
    }

    @Test
    void platformAccountWithoutPermissionGets403() throws Exception {
        loginWithResources("contract_user");
        String token = token("contract_user", 0);

        MockHttpServletRequestBuilder request = json(post(BASE + "/brands"), "{\"brandName\":\"X\"}");
        request.header("Authorization", "Bearer " + token);
        MvcResult result = mvc.perform(request).andReturn();

        assertEquals(403, result.getResponse().getStatus());
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals(403, body.path("code").asInt());
        assertEquals(0, jdbc.queryForObject("select count(*) from product_brand", Integer.class));
        // 读取只需要登录
        assertEquals(0, call(get(BASE + "/brands"), token).path("code").asInt());
    }

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        JsonNode body = call(get(BASE + "/brands"), null);

        assertEquals(401, body.path("code").asInt());
    }

    @Test
    void relationshipAndSpecificationEndpointsFollowTheSameContract() throws Exception {
        loginAsAdmin("contract_admin");
        String admin = token("contract_admin", 0);
        long productId = createBrandAndCategoryAndProduct(admin);

        JsonNode specs = call(json(put(BASE + "/products/" + productId + "/specifications"),
                "{\"items\":[{\"specKey\":\"rated_voltage\",\"specLabel\":\"Rated Voltage\",\"specValue\":\"24\",\"specUnit\":\"V DC\"}]}"), admin);
        assertEquals(0, specs.path("code").asInt());
        assertEquals("rated_voltage", specs.path("data").get(0).path("specKey").asText());

        JsonNode rel = call(json(post(BASE + "/products/" + productId + "/relationships"),
                "{\"relatedMpn\":\"6ES7212-1AB23-0XB0\",\"relationshipType\":2}"), admin);
        assertEquals(0, rel.path("code").asInt());
        assertTrue(rel.path("data").path("relatedProductId").isMissingNode() || rel.path("data").path("relatedProductId").isNull());

        JsonNode self = call(json(post(BASE + "/products/" + productId + "/relationships"),
                "{\"relatedMpn\":\"6es7 214 1bd23 0xb0\",\"relationshipType\":4}"), admin);
        assertEquals("RELATIONSHIP_INVALID", self.path("data").path("errorCode").asText());
    }

    @Test
    void moneyResponsesCarryTheCurrencyCode() throws Exception {
        loginAsAdmin("contract_admin");
        String admin = token("contract_admin", 0);
        long productId = createBrandAndCategoryAndProduct(admin);

        JsonNode saved = call(json(put(BASE + "/products/" + productId + "/reference-price"),
                "{\"priceOriginal\":100.00,\"currencyCode\":\"USD\",\"exchangeRate\":7.123456}"), admin);

        assertEquals(0, saved.path("code").asInt());
        assertEquals("USD", saved.path("data").path("currencyCode").asText());
        assertEquals(0, saved.path("data").path("priceCny").decimalValue().compareTo(new java.math.BigDecimal("712.35")));
        JsonNode empty = call(get(BASE + "/products/" + productId + "/logistics"), admin);
        assertEquals(0, empty.path("code").asInt());
        assertTrue(empty.path("data").path("netWeightKg").isMissingNode() || empty.path("data").path("netWeightKg").isNull());
    }

    @Test
    void pendingFaqNeverLeavesTheServerForTenantTokens() throws Exception {
        loginAsAdmin("contract_admin");
        String platform = token("contract_admin", 0);
        long productId = createBrandAndCategoryAndProduct(platform);
        call(json(post(BASE + "/products/" + productId + "/faqs"), "{\"question\":\"Manual one?\",\"answer\":\"A\"}"), platform);
        jdbc.update("insert into product_faq (tenant_id, product_id, question, answer, source) values (0, ?, 'Pending one?', 'AI', 3)", productId);
        String tenant = token("contract_admin", 1002);

        JsonNode tenantView = call(get(BASE + "/products/" + productId + "/faqs"), tenant);
        JsonNode platformView = call(get(BASE + "/products/" + productId + "/faqs"), platform);

        assertEquals(1, tenantView.path("data").size());
        assertEquals(2, platformView.path("data").size());
        assertFalse(tenantView.toString().contains("Pending one?"));
        assertEquals(0, statusOfPatch(productId, platform));
    }

    private int statusOfPatch(long productId, String token) throws Exception {
        long pending = jdbc.queryForObject("select id from product_faq where source = 3", Long.class);
        JsonNode approved = call(patch(BASE + "/products/" + productId + "/faqs/" + pending + "/approve"), token);
        assertEquals(2, approved.path("data").path("source").asInt());
        return approved.path("code").asInt();
    }

    @Test
    void multipartUploadWorksEndToEndAndTenantUploadIsRefused() throws Exception {
        loginAsAdmin("contract_admin");
        String platform = token("contract_admin", 0);
        long productId = createBrandAndCategoryAndProduct(platform);
        MockMultipartFile file = new MockMultipartFile("file", "front.png", "image/png", PNG);

        JsonNode uploaded = call(multipart(BASE + "/products/" + productId + "/media/upload").file(file)
                .param("mediaType", "1").param("title", "front").param("setMain", "true"), platform);

        assertEquals(0, uploaded.path("code").asInt(), uploaded.toString());
        assertEquals(1, uploaded.path("data").path("isMain").asInt());
        assertTrue(uploaded.path("data").path("fileUrl").asText().startsWith("/uploads/product/"));
        assertEquals(1, uploaded.path("data").path("storageType").asInt());

        JsonNode refused = call(multipart(BASE + "/products/" + productId + "/media/upload").file(file)
                .param("mediaType", "1"), token("contract_admin", 1001));
        assertEquals("PLATFORM_ADMIN_REQUIRED", refused.path("data").path("errorCode").asText());
        assertEquals(1, jdbc.queryForObject("select count(*) from product_media", Integer.class));

        MockMultipartFile bogus = new MockMultipartFile("file", "evil.png", "image/png", new byte[]{'M', 'Z', 0, 0});
        JsonNode rejected = call(multipart(BASE + "/products/" + productId + "/media/upload").file(bogus)
                .param("mediaType", "1"), platform);
        assertEquals("MEDIA_FILE_INVALID", rejected.path("data").path("errorCode").asText());
    }

    @Test
    void countryCatalogIsReadableByTenantTokensAndCarriesBothNames() throws Exception {
        String tenant = token("contract_tenant_user", 1001);

        JsonNode list = call(get(BASE + "/countries"), tenant);

        assertEquals(0, list.path("code").asInt());
        assertEquals(249, list.path("data").size());
        JsonNode first = list.path("data").get(0);
        assertTrue(first.has("code") && first.has("nameEn") && first.has("nameZh"));
        boolean hasTaiwan = false;
        for (JsonNode c : list.path("data")) {
            if ("Taiwan, China".equals(c.path("nameEn").asText())) {
                hasTaiwan = true;
                assertEquals("中国台湾", c.path("nameZh").asText());
            }
        }
        assertTrue(hasTaiwan);
        assertEquals(401, call(get(BASE + "/countries"), null).path("code").asInt());
    }

    @Test
    void brandAndCategoryCarryDescriptionAndValidationErrorsUseParamInvalid() throws Exception {
        loginAsAdmin("contract_admin");
        String admin = token("contract_admin", 0);

        JsonNode brand = call(json(post(BASE + "/brands"),
                "{\"brandName\":\"Siemens\",\"country\":\"Germany\",\"brandColor\":\"#009999\","
                        + "\"description\":\"德国工业自动化厂商\"}"), admin);
        assertEquals(0, brand.path("code").asInt());
        assertEquals("德国工业自动化厂商", brand.path("data").path("description").asText());

        JsonNode badCountry = call(json(post(BASE + "/brands"),
                "{\"brandName\":\"X\",\"country\":\"Deutschland1\"}"), admin);
        assertEquals("PARAM_INVALID", badCountry.path("data").path("errorCode").asText());
        JsonNode badColor = call(json(post(BASE + "/brands"),
                "{\"brandName\":\"Y\",\"brandColor\":\"red\"}"), admin);
        assertEquals("PARAM_INVALID", badColor.path("data").path("errorCode").asText());

        JsonNode category = call(json(post(BASE + "/categories"),
                "{\"categoryCode\":\"servo\",\"categoryName\":\"Servo\",\"description\":\"伺服驱动器与电机\"}"), admin);
        assertEquals("伺服驱动器与电机", category.path("data").path("description").asText());
        assertEquals("伺服驱动器与电机",
                call(get(BASE + "/categories/options"), admin).path("data").get(0).path("description").asText());
    }

    @Test
    void optionsAndStatusEndpointsAlsoFollowTheContract() throws Exception {
        loginAsAdmin("contract_admin");
        String admin = token("contract_admin", 0);
        long brand = call(json(post(BASE + "/brands"), "{\"brandName\":\"Siemens\"}"), admin).path("data").path("id").asLong();

        JsonNode options = call(get(BASE + "/brands/options"), admin);
        assertEquals(0, options.path("code").asInt());
        assertEquals(1, options.path("data").size());

        JsonNode disabled = call(json(patch(BASE + "/brands/" + brand + "/status"), "{\"status\":0}"), admin);
        assertEquals(0, disabled.path("code").asInt());
        assertEquals(0, call(get(BASE + "/brands/options"), admin).path("data").size());

        int badStatus = statusOf(json(patch(BASE + "/brands/" + brand + "/status"), "{}"), admin);
        assertEquals(400, badStatus);
    }
}
