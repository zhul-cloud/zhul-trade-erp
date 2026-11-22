package com.zhul.erp.modules.inquiry;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.inquiry.support.PriceKeys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** 询价统计 → 无货分析（spec inquiry/sourcing-stats「无货分析」） */
class NoStockStatsContractTest extends InquiryContractSupport {

    private static final String API = "/api/v1/inquiry/sourcing-stats/no-stock";

    @Autowired private PriceKeys priceKeys;

    private void quote(String brand, String model, boolean noStock, Integer reason, String quotedAt) {
        TenantContext.setTenantId(0);
        jdbc.update("insert into sourcing_quote (tenant_id, brand, brand_key, model, model_key, channel, currency_code, unit_price, "
                        + "unit_price_cny, no_stock, no_stock_reason, quoted_by, quoted_at, status) values (0, ?, ?, ?, ?, 2, 'CNY', ?, ?, ?, ?, ?, ?, 2)",
                brand, priceKeys.brand(brand).brandKey(), model, PriceKeys.model(model), noStock ? null : "100", noStock ? null : "100",
                noStock ? 1 : 0, reason, BUYER_LIN, quotedAt);
    }

    private static void eq(String expected, JsonNode n) {
        assertEquals(0, new BigDecimal(expected).compareTo(n.decimalValue()), "期望 " + expected + "，实际 " + n);
    }

    @Test
    void weekly_latestQuotePerModel_byBrandWithReasons_trendAndPrevious() throws Exception {
        quote("Siemens", "6ES7 214-1AG40", true, 1, "2026-10-13 09:00:00");
        quote("Siemens", "6ES7214-1AG40", false, null, "2026-10-15 09:00:00");
        quote("Siemens", "6ES7 315-2AH14", true, 2, "2026-10-14 10:00:00");
        quote("Omron", "E3Z-D61", true, 1, "2026-10-16 10:00:00");
        quote("Omron", "E3Z-D62", false, null, "2026-10-16 11:00:00");
        quote("Siemens", "6ES7 412-2XJ05", true, 1, "2026-10-07 11:00:00");
        loginAsAdmin("it_ns_admin");
        String admin = token("it_ns_admin");

        JsonNode r = ok(call(get(API).param("period", "WEEK").param("date", "2026-10-14"), admin));
        assertEquals("2026-10-12", r.path("dateFrom").asText());
        assertEquals("2026-10-18", r.path("dateTo").asText());
        assertEquals(4, r.path("totals").path("models").asInt(), "同一型号写法不同算一个：" + r);
        assertEquals(2, r.path("totals").path("noStock").asInt(), "按最后一次回价，后来有价的不算无货");
        eq("50.0", r.path("totals").path("rate"));
        assertEquals(2, r.path("totals").path("brandCount").asInt());
        assertEquals(1, r.path("previous").path("noStock").asInt());
        eq("100.0", r.path("previous").path("rate"));
        assertEquals(2, r.path("brands").size());
        JsonNode siemens = r.path("brands").get(0).path("name").asText().equals("Siemens") ? r.path("brands").get(0) : r.path("brands").get(1);
        assertEquals(2, siemens.path("models").asInt());
        assertEquals(1, siemens.path("noStock").asInt());
        assertEquals(1, siemens.path("reasons").size());
        assertEquals(1, r.path("categories").size(), "没关联品类的归未分类");
        assertEquals("未分类", r.path("categories").get(0).path("name").asText());
        assertEquals(8, r.path("trend").size());
        assertEquals("10-12", r.path("trend").get(7).path("label").asText());
        assertEquals("10-05", r.path("trend").get(6).path("label").asText());

        JsonNode m = ok(call(get(API).param("period", "MONTH").param("date", "2026-10-31"), admin));
        assertEquals(5, m.path("totals").path("models").asInt());
        assertEquals(3, m.path("totals").path("noStock").asInt());
        assertEquals("2026-10", m.path("trend").get(7).path("label").asText());
        assertTrue(call(get(API).param("period", "YEAR"), admin).path("message").asText().contains("按周或按月"));

        // 点品类跳到询价记录：按一级品类筛选（关联不到时为空）
        JsonNode h = ok(call(json(post(HISTORY + "/page"), "{\"noStock\":true,\"categoryId\":-1}"), admin));
        assertEquals(0, h.path("total").asInt());
    }
}
