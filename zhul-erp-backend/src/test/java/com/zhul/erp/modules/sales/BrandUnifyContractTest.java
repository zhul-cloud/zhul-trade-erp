package com.zhul.erp.modules.sales;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhul.erp.modules.inquiry.support.PriceKeys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * openspec brand-unify：单据里认不出的品牌写法进入待确认品牌；设为别名后单据统一成正式品牌名
 * （已发给客户的报价单不改）；标为不是品牌后不再列出、单据保持原样；询盘确认时识别到的品牌存正式名。
 */
class BrandUnifyContractTest extends SalesContractSupport {

    private static final String PENDING = "/api/v1/masterdata/pending-brands";
    private static final String VARIANT = "Siemens Automation Test";
    private static final String NOT_BRAND = "未知品牌";

    @Autowired
    private PriceKeys priceKeys;

    @AfterEach
    void cleanBrandWords() {
        jdbc.update("delete from product_brand_alias where tenant_id = 0 and alias_key = ?", VARIANT.toLowerCase());
        jdbc.update("delete from brand_text_ignored where tenant_id = 0 and text_key = ?", NOT_BRAND);
    }

    private long siemens() {
        return jdbc.queryForObject("select id from product_brand where tenant_id = 0 and brand_name = 'Siemens' and deleted_at is null",
                Long.class);
    }

    private JsonNode pending(String key) throws Exception {
        for (JsonNode p : ok(call(get(PENDING), admin))) {
            if (p.path("pendingKey").asText().equals(key)) {
                return p;
            }
        }
        return null;
    }

    private void setBrand(long quotationId, String brand) {
        jdbc.update("update quotation_item set brand = ? where quotation_id = ?", brand, quotationId);
        jdbc.update("update inquiry_item set brand = ? where customer_inquiry_id = ?", brand, inquiryOf(quotationId));
    }

    private String brandOf(String table, String column, long id) {
        return jdbc.queryForObject("select brand from " + table + " where " + column + " = ? limit 1", String.class, id);
    }

    @Test
    void linkAsAlias_unifiesDocs_sentQuotationKept() throws Exception {
        long c = customer("PRIME CONTROLS", "United States");
        long draft = quotation(c, 1, "USD", null, l("6ES7214-1AG40-0XB0", 1, "2640", "468"));
        long sent = quotation(c, 2, "USD", null, l("6ES7215-1AG40-0XB0", 1, "2640", "468"));
        setBrand(draft, VARIANT);
        setBrand(sent, "  " + VARIANT.toUpperCase() + " ");

        // 两张询盘的写法按比较键合成一条，带出现次数和相近建议
        String key = VARIANT.toLowerCase();
        JsonNode p = pending(key);
        assertNotNull(p, "单据里认不出的写法进入待确认品牌");
        assertEquals(2, p.path("inquiryCount").asInt());
        assertEquals(0, p.path("supplierCount").asInt());

        JsonNode done = ok(call(json(post(PENDING + "/alias-batch"), write(Map.of("pendingKeys", List.of(key), "brandId", siemens()))), admin));
        assertEquals(1, done.asInt());

        assertEquals("Siemens", brandOf("inquiry_item", "customer_inquiry_id", inquiryOf(draft)));
        assertEquals("Siemens", brandOf("inquiry_item", "customer_inquiry_id", inquiryOf(sent)));
        assertEquals("Siemens", brandOf("quotation_item", "quotation_id", draft), "报价单草稿跟着改");
        assertEquals("  " + VARIANT.toUpperCase() + " ", brandOf("quotation_item", "quotation_id", sent), "已发给客户的报价单不改");
        assertNull(pending(key), "处理后不再列出");

        // 以后询盘里同样的写法直接认成正式品牌名
        assertEquals("Siemens", priceKeys.brandText(priceKeys.brand(VARIANT), VARIANT));
        assertEquals("Not In Library", priceKeys.brandText(priceKeys.brand(" Not In Library "), " Not In Library "));
    }

    @Test
    void ignore_hidesWord_docsKept() throws Exception {
        long c = customer("PRIME CONTROLS", "United States");
        long q = quotation(c, 1, "USD", null, l("6ES7214-1AG40-0XB0", 1, "2640", "468"));
        setBrand(q, NOT_BRAND);

        JsonNode p = pending(NOT_BRAND);
        assertNotNull(p);
        assertEquals("notBrand", p.path("suggestKind").asText());

        assertEquals(1, ok(call(json(post(PENDING + "/ignore"), write(Map.of("pendingKeys", List.of(NOT_BRAND)))), admin)).asInt());
        assertNull(pending(NOT_BRAND));
        assertEquals(NOT_BRAND, brandOf("inquiry_item", "customer_inquiry_id", inquiryOf(q)), "单据保持原样");

        // 已处理的写法再提交：提示刷新
        assertEquals("待确认品牌不存在或已被处理，请刷新后再试",
                fail(call(json(post(PENDING + "/ignore"), write(Map.of("pendingKeys", List.of(NOT_BRAND)))), admin)).path("message").asText());
    }
}
