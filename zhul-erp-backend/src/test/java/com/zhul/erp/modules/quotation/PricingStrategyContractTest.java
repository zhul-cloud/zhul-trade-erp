package com.zhul.erp.modules.quotation;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhul.erp.support.TenantContractSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/** 定价策略（spec quotation/pricing-rule）、系统汇率（spec system/exchange-rate）与字典英文名称 */
class PricingStrategyContractTest extends TenantContractSupport {

    private static final String STRATEGY = "/api/v1/quotation/pricing-strategy";
    private static final String RATES = "/api/v1/system/exchange-rates";
    private static final int TENANT_A = 99201;
    private static final int TENANT_B = 99202;
    private static final String DICT_CODE = "IT_LOST_EN";

    @BeforeEach
    void setUp() {
        cleanup();
        loginAsAdmin("it_price_admin");
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    private void cleanup() {
        for (String t : new String[] {"pricing_condition_margin", "pricing_amount_tier", "exchange_rate", "exchange_rate_log"}) {
            jdbc.update("delete from " + t + " where tenant_id in (?, ?)", TENANT_A, TENANT_B);
        }
        jdbc.update("delete from sys_config where tenant_id in (?, ?) and config_key like 'quotation.%'", TENANT_A, TENANT_B);
        jdbc.update("delete from dict_item where item_code = ?", DICT_CODE);
    }

    private JsonNode strategy(String token) throws Exception {
        return ok(call(get(STRATEGY), token));
    }

    private static JsonNode condition(JsonNode strategy, int code) {
        for (JsonNode c : strategy.path("conditions")) {
            if (c.path("itemCondition").asInt() == code) {
                return c;
            }
        }
        throw new AssertionError("没有货况 " + code);
    }

    /** 把读到的策略原样转成保存请求，再按需修改 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> asRequest(JsonNode strategy) throws Exception {
        Map<String, Object> req = objectMapper.convertValue(strategy, LinkedHashMap.class);
        List<Map<String, Object>> conditions = new ArrayList<>();
        for (Map<String, Object> c : (List<Map<String, Object>>) req.get("conditions")) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("itemCondition", c.get("itemCondition"));
            m.put("marginRate", c.get("marginRate"));
            m.put("floorRate", c.get("floorRate"));
            conditions.add(m);
        }
        req.put("conditions", conditions);
        return req;
    }

    @SuppressWarnings("unchecked")
    private static void setCondition(Map<String, Object> req, int code, String margin, String floor) {
        for (Map<String, Object> c : (List<Map<String, Object>>) req.get("conditions")) {
            if (((Number) c.get("itemCondition")).intValue() == code) {
                c.put("marginRate", margin == null ? null : new BigDecimal(margin));
                c.put("floorRate", floor == null ? null : new BigDecimal(floor));
            }
        }
    }

    /** 接口不输出空值字段 */
    private static boolean absent(JsonNode n) {
        return n.isMissingNode() || n.isNull();
    }

    @Test
    void platformDefaultsFromSop() throws Exception {
        JsonNode s = strategy(token("it_price_admin", TENANT_A));
        assertEquals(0, new BigDecimal("10").compareTo(condition(s, 1).path("marginRate").decimalValue()));
        assertEquals(0, new BigDecimal("15").compareTo(condition(s, 3).path("floorRate").decimalValue()));
        assertEquals(0, new BigDecimal("62").compareTo(condition(s, 6).path("marginRate").decimalValue()));
        assertTrue(absent(condition(s, 7).path("marginRate")), "待确认不给建议值");
        assertEquals(3, s.path("tiers").size());
        assertEquals(0, new BigDecimal("100").compareTo(s.path("tiers").get(0).path("maxCost").decimalValue()));
        assertTrue(s.path("hints").path("returningCustomer").asBoolean());
        assertEquals("VEGA", s.path("premiumBrands").get(0).asText());
    }

    @Test
    void floorAboveSuggestedIsRejected_andTenantEditDoesNotAffectOthers() throws Exception {
        String a = token("it_price_admin", TENANT_A);
        Map<String, Object> req = asRequest(strategy(a));
        setCondition(req, 3, "20", "25");
        assertEquals("红线不能高于建议毛利率", fail(call(json(put(STRATEGY), write(req)), a)).path("message").asText());
        setCondition(req, 3, "96", "10");
        assertTrue(fail(call(json(put(STRATEGY), write(req)), a)).path("message").asText().contains("0–95%"));

        setCondition(req, 3, "22", "15");
        req.put("tiers", List.of(Map.of("maxCost", 150, "marginRate", 40)));
        req.put("premiumBrands", List.of("VEGA", " vega ", "Endress+Hauser"));
        req.put("hints", Map.of("discontinuedUrgent", false, "premiumBrand", true, "returningCustomer", true, "toConfirm", true));
        JsonNode saved = ok(call(json(put(STRATEGY), write(req)), a));
        assertEquals(0, new BigDecimal("22").compareTo(condition(saved, 3).path("marginRate").decimalValue()));
        assertEquals(1, saved.path("tiers").size());
        assertEquals(2, saved.path("premiumBrands").size(), "大小写重复的品牌去重");
        assertFalse(saved.path("hints").path("discontinuedUrgent").asBoolean());

        JsonNode b = strategy(token("it_price_admin", TENANT_B));
        assertEquals(0, new BigDecimal("20").compareTo(condition(b, 3).path("marginRate").decimalValue()), "其他租户仍用平台默认");
        assertEquals(3, b.path("tiers").size());
        assertTrue(b.path("hints").path("discontinuedUrgent").asBoolean());
    }

    @Test
    void modifyExchangeRate_keepsLog_andRejectsIllegalValues() throws Exception {
        String a = token("it_price_admin", TENANT_A);
        JsonNode list = ok(call(get(RATES), a));
        assertEquals(5, list.size(), "USD、EUR、GBP、JPY、RUB");
        assertEquals("RUB", list.get(4).path("currencyCode").asText());
        assertTrue(absent(list.get(0).path("rate")), "新租户还没有设置汇率");

        ok(call(json(put(RATES + "/USD"), "{\"rate\":7.15}"), a));
        JsonNode usd = ok(call(json(put(RATES + "/usd"), "{\"rate\":7.18}"), a));
        assertEquals(0, new BigDecimal("7.180000").compareTo(usd.path("rate").decimalValue()));
        assertEquals("手动录入", usd.path("sourceName").asText());

        JsonNode logs = ok(call(get(RATES + "/USD/logs"), a));
        assertEquals(2, logs.size());
        assertEquals(0, new BigDecimal("7.150000").compareTo(logs.get(0).path("oldRate").decimalValue()));
        assertEquals(0, new BigDecimal("7.180000").compareTo(logs.get(0).path("newRate").decimalValue()));
        assertTrue(absent(logs.get(1).path("oldRate")), "首次设置没有原值");

        assertEquals("汇率需要大于 0", fail(call(json(put(RATES + "/USD"), "{\"rate\":0}"), a)).path("message").asText());
        assertTrue(fail(call(json(put(RATES + "/USD"), "{\"rate\":7.1234567}"), a)).path("message").asText().contains("6 位小数"));
        assertTrue(fail(call(json(put(RATES + "/HKD"), "{\"rate\":0.9}"), a)).path("message").asText().contains("不支持"));

        assertTrue(absent(ok(call(get(RATES), token("it_price_admin", TENANT_B))).get(0).path("rate")), "汇率按租户隔离");
    }

    @Test
    void dictItemEnglishName() throws Exception {
        String admin = token("it_price_admin", 0);
        Integer typeId = jdbc.queryForObject("select id from dict_type where dict_type = 'quotation_lost_reason' and tenant_id = 0", Integer.class);
        ok(call(json(post("/api/v1/system/dict-items"), write(Map.of("dictTypeId", typeId, "itemCode", DICT_CODE,
                "itemName", "【其他】测试原因", "itemNameEn", " Test reason ", "itemValue", "99"))), admin));
        JsonNode items = ok(call(get("/api/v1/system/dict-items").param("dictType", "quotation_lost_reason"), admin));
        JsonNode found = null;
        for (JsonNode i : items) {
            if (DICT_CODE.equals(i.path("itemCode").asText())) {
                found = i;
            }
        }
        assertEquals("Test reason", found == null ? null : found.path("itemNameEn").asText());
        JsonNode condition = ok(call(get("/api/v1/system/dict-items").param("dictType", "inquiry_item_condition"), admin)).get(0);
        assertEquals("original new", condition.path("itemNameEn").asText());
    }
}
