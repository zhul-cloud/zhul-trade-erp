package com.zhul.erp.modules.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhul.erp.common.constants.RedisKeyConstants;
import com.zhul.erp.modules.aitask.dto.AiTaskCallbackRequest;
import com.zhul.erp.modules.aitask.service.AiOrchestratorClient;
import com.zhul.erp.modules.aitask.service.AiTaskService;
import com.zhul.erp.modules.inquiry.InquiryContractSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * openspec content-ai-batch-hs：品类默认海关编码（细分品类沿用上级）、目的国进口关税参考（按 6 位编码共用）、
 * 商品海关信息显示品类默认编码与关税参考、AI 补全只给建议。
 */
class HsTariffContractTest extends InquiryContractSupport {

    private static final String CATEGORIES = "/api/v1/product/categories";
    private static final String PRODUCTS = "/api/v1/product/products";

    @MockBean private AiOrchestratorClient orchestrator;
    @Autowired private AiTaskService aiTaskService;

    private String admin;

    @BeforeEach
    void seed() {
        cleanup();
        loginAsAdmin("it_hs_admin");
        admin = token("it_hs_admin");
        when(orchestrator.submit(any(), anyLong(), any(), any())).thenAnswer(inv -> new CompletableFuture<>());
    }

    @AfterEach
    void cleanup() {
        String ps = "(select id from product where brand_id in (select id from product_brand where brand_name = 'ITHS'))";
        jdbc.update("delete from product_customs where product_id in " + ps);
        jdbc.update("delete from product where brand_id in (select id from product_brand where brand_name = 'ITHS')");
        jdbc.update("delete from product_brand where brand_name = 'ITHS'");
        jdbc.update("delete from product_category where category_code like 'iths_%'");
        jdbc.update("delete from hs_tariff_ref where hs6 in ('853650', '853710')");
        jdbc.update("delete from ai_task where skill_id = 'hs-tariff-ref'");
        removeAiConfig(0);
    }

    private long category(String code, Long parentId, String hsCode) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("categoryCode", code);
        body.put("categoryName", code);
        body.put("categoryNameZh", "测试" + code);
        body.put("parentId", parentId);
        body.put("hsCode", hsCode);
        return ok(call(json(post(CATEGORIES), write(body)), admin)).path("id").asLong();
    }

    private JsonNode treeNode(long id) throws Exception {
        for (JsonNode root : ok(call(get(CATEGORIES + "/tree"), admin))) {
            if (root.path("id").asLong() == id) {
                return root;
            }
            for (JsonNode c : root.path("children")) {
                if (c.path("id").asLong() == id) {
                    return c;
                }
            }
        }
        throw new AssertionError("品类不在树里：" + id);
    }

    private static Map<String, Object> rate(String country, String rate, String note) {
        return Map.of("countryCode", country, "rate", rate, "note", note);
    }

    @Test
    void categoryDefault_inherited_tariffShared_productCustomsShowsReference() throws Exception {
        long sensors = category("iths_sensors", null, " 8536.50.90 00 ");
        long photo = category("iths_photo", sensors, null);
        assertEquals("8536509000", jdbc.queryForObject("select hs_code from product_category where id = ?", String.class, sensors),
                "只存数字");
        assertEquals("HS_CODE_INVALID", fail(call(json(post(CATEGORIES), write(Map.of("categoryCode", "iths_bad",
                "categoryName", "bad", "hsCode", "85AB"))), admin)).path("data").path("errorCode").asText());

        // 关税参考按 6 位编码保存：填了税率的记核对日期，清空表示未填
        JsonNode saved = ok(call(json(put(CATEGORIES + "/tariffs/853650"), write(Map.of("rates", List.of(
                rate("MX", "0%", ""), rate("IN", "7.5%", "另加社会福利附加税"), rate("IR", "", ""))))), admin));
        assertEquals(9, saved.path("rates").size(), "固定 9 个国家");
        assertEquals("MX", saved.path("rates").get(0).path("countryCode").asText());
        assertEquals("墨西哥", saved.path("rates").get(0).path("countryName").asText());
        assertFalse(saved.path("rates").get(0).path("checkedAt").isNull());
        assertTrue(saved.path("categories").toString().contains("测试iths_sensors"));
        assertEquals("不支持的国家：US", fail(call(json(put(CATEGORIES + "/tariffs/853650"), write(Map.of("rates",
                List.of(rate("US", "1%", ""))))), admin)).path("message").asText());

        // 细分品类没填时沿用上级：列表显示实际编码与关税概况
        JsonNode node = treeNode(photo);
        assertEquals("", node.path("hsCode").asText());
        assertEquals("8536509000", node.path("effectiveHsCode").asText());
        assertTrue(node.path("hsInherited").asBoolean());
        assertEquals(2, node.path("tariffFilled").asInt());
        assertEquals(9, node.path("tariffTotal").asInt());
        assertEquals("0%～7.5%", node.path("tariffRange").asText());
        assertTrue(node.path("tariffMissing").toString().contains("伊朗"));

        // 修改：不传编码保持不变，传空字符串清空
        long id = sensors;
        ok(call(json(put(CATEGORIES + "/" + id), write(Map.of("categoryCode", "iths_sensors", "categoryName", "iths_sensors",
                "categoryNameZh", "测试iths_sensors"))), admin));
        assertEquals("8536509000", jdbc.queryForObject("select hs_code from product_category where id = ?", String.class, id));

        // 商品海关信息：没填 HS 编码时按品类默认编码显示关税参考；填了按自己的
        jdbc.update("insert into product_brand (tenant_id, brand_name, status) values (0, 'ITHS', 1)");
        long brand = jdbc.queryForObject("select id from product_brand where brand_name = 'ITHS'", Long.class);
        long product = ok(call(json(post(PRODUCTS), write(Map.of("brandId", brand, "categoryId", photo, "mpnRaw", "E3Z-HS"))), admin))
                .path("id").asLong();
        JsonNode customs = ok(call(get(PRODUCTS + "/" + product + "/customs"), admin));
        assertEquals("8536509000", customs.path("categoryHsCode").asText());
        assertEquals("测试iths_sensors", customs.path("categoryHsFrom").asText());
        assertEquals("category", customs.path("tariffBy").asText());
        assertEquals("853650", customs.path("tariff").path("hs6").asText());
        assertEquals("7.5%", customs.path("tariff").path("rates").get(1).path("rate").asText());
        ok(call(json(put(PRODUCTS + "/" + product + "/customs"), write(Map.of("hsCode", "85371011"))), admin));
        customs = ok(call(get(PRODUCTS + "/" + product + "/customs"), admin));
        assertEquals("product", customs.path("tariffBy").asText());
        assertEquals("853710", customs.path("tariff").path("hs6").asText());
        assertEquals("", customs.path("tariff").path("rates").get(0).path("rate").asText(), "这个编码还没有关税参考");

        // 租户账号能读，不能改
        String other = jwtUtils.generateToken(Map.of("tenantId", 1001), "it_hs_admin", 3600);
        redis.opsForValue().set(RedisKeyConstants.TOKEN_PREFIX + other, "1");
        assertEquals(9, ok(call(get(CATEGORIES + "/tariffs/853650"), other)).path("rates").size());
        assertTrue(fail(call(json(put(CATEGORIES + "/tariffs/853650"), write(Map.of("rates", List.of(rate("MX", "1%", ""))))),
                other)).path("message").asText().contains("平台"));
    }

    @Test
    void aiFill_onlyMissingCountries_resultIsSuggestion() throws Exception {
        seedAiConfig(0);
        category("iths_ai", null, "853650");
        ok(call(json(put(CATEGORIES + "/tariffs/853650"), write(Map.of("rates", List.of(rate("MX", "0%", ""))))), admin));
        JsonNode started = ok(call(post(CATEGORIES + "/tariffs/853650/ai"), admin));
        long taskId = started.path("taskId").asLong();
        org.mockito.ArgumentCaptor<String> captor = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(orchestrator, org.mockito.Mockito.timeout(5000))
                .submit(org.mockito.ArgumentMatchers.eq("hs-tariff-ref"), org.mockito.ArgumentMatchers.eq(taskId), captor.capture(), any());
        String input = captor.getValue();
        assertFalse(input.contains("\"MX\""), "已填的国家不再查");
        assertTrue(input.contains("\"IN\"") && input.contains("853650"));

        AiTaskCallbackRequest req = new AiTaskCallbackRequest();
        req.setStatus("success");
        req.setOutput(Map.of("rates", List.of(
                Map.of("country", "IN", "rate", "7.5%", "note", "另加附加税", "source", "https://example.org/in"),
                Map.of("country", "XX", "rate", "1%", "note", "", "source", ""))));
        aiTaskService.handleCallback(taskId, req);
        JsonNode result = ok(call(get(CATEGORIES + "/tariffs/ai/" + taskId), admin));
        assertEquals(3, result.path("status").asInt());
        assertEquals(1, result.path("rates").size(), "不认识的国家丢弃");
        assertEquals("印度", result.path("rates").get(0).path("countryName").asText());
        assertEquals(0, jdbc.queryForObject("select count(*) from hs_tariff_ref where hs6 = '853650' and country_code = 'IN'",
                Integer.class), "AI 结果只是建议，人核对后才保存");
    }

    private static JsonNode fail(JsonNode res) {
        assertTrue(res.path("code").asInt() != 0, "应当失败：" + res);
        return res;
    }
}
