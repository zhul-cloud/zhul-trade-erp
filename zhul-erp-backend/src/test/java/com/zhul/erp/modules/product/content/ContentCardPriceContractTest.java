package com.zhul.erp.modules.product.content;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhul.erp.modules.inquiry.InquiryContractSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * spec product/product-content「商品内容规范 v2」：资料包「## 商品平台参考价」写商品卡片的平台参考价；
 * 没有这一节时，「## 平台参考价」六列表格（公开挂牌价）按全新最低价生成商品卡片的平台参考价
 */
class ContentCardPriceContractTest extends InquiryContractSupport {

    private static final String TASKS = "/api/v1/product/content-tasks";
    private static final String PRODUCTS = "/api/v1/product/products";
    private static final String BRAND = "ITCCardPrice";

    private String admin;
    private long product;

    @BeforeEach
    void seed() throws Exception {
        cleanup();
        jdbc.update("insert into product_brand (tenant_id, brand_name, status) values (0, ?, 1)", BRAND);
        long brand = jdbc.queryForObject("select id from product_brand where brand_name = ?", Long.class, BRAND);
        jdbc.update("insert into product_category (tenant_id, category_code, category_name, category_name_zh, status) "
                + "values (0, 'itc-cp-plc', 'PLC', 'PLC', 1)");
        long category = jdbc.queryForObject("select id from product_category where category_code = 'itc-cp-plc'", Long.class);
        loginAsAdmin("it_cp_admin");
        admin = token("it_cp_admin");
        product = ok(call(json(post(PRODUCTS), write(Map.of("brandId", brand, "categoryId", category, "mpnRaw", "CP-100"))), admin))
                .path("id").asLong();
    }

    @AfterEach
    void cleanup() {
        String products = "(select id from product where brand_id in (select id from product_brand where brand_name = '" + BRAND + "'))";
        for (String t : new String[] {"product_content_import", "product_fact_task", "product_listing_price", "product_reference_price"}) {
            jdbc.update("delete from " + t + " where product_id in " + products);
        }
        jdbc.update("delete from product where brand_id in (select id from product_brand where brand_name = ?)", BRAND);
        jdbc.update("delete from product_brand where brand_name = ?", BRAND);
        jdbc.update("delete from product_category where category_code = 'itc-cp-plc'");
        jdbc.update("delete from exchange_rate where tenant_id = 0 and currency_code = 'USD'");
    }

    private static String listings(String... rows) {
        StringBuilder b = new StringBuilder("## 平台参考价\n| 价格类别 | 平台/网站 | 成色 | 参考价格（USD） | 来源原价 | 来源链接 |\n"
                + "| --- | --- | --- | ---: | --- | --- |\n");
        for (String r : rows) {
            b.append(r).append('\n');
        }
        return b.append('\n').toString();
    }

    private static String facts(String updated, String body) {
        return "---\nschema: 2\ntype: facts\nbrand: " + BRAND + "\nmodel: CP-100\nlang: zh\n"
                + (updated == null ? "" : "updated: " + updated + "\n") + "---\n\n# X\n\n" + body;
    }

    private static String card(String currency, String price, String rate, String source, String date) {
        StringBuilder b = new StringBuilder("## 商品平台参考价\n\n| 字段 | 内容 |\n|---|---|\n");
        b.append("| 币种 | ").append(currency).append(" |\n");
        b.append("| 参考价（原币） | ").append(price).append(" |\n");
        b.append("| 汇率（原币 → 人民币） | ").append(rate).append(" |\n");
        b.append("| 价格来源 | ").append(source).append(" |\n");
        if (date != null) {
            b.append("| 取价日期 | ").append(date).append(" |\n");
        }
        return b.append('\n').toString();
    }

    private static Map<String, Object> file(String content) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("fileName", "facts.md");
        f.put("content", content);
        return f;
    }

    private JsonNode preview(String content) throws Exception {
        return ok(call(json(post(TASKS + "/facts/preview"), write(Map.of("files", List.of(file(content))))), admin)).get(0);
    }

    private void confirm(String content) throws Exception {
        JsonNode r = ok(call(json(post(TASKS + "/facts/confirm"), write(Map.of("files", List.of(file(content))))), admin));
        assertEquals(1, r.path("succeeded").asInt(), r.toString());
    }

    private JsonNode price() throws Exception {
        return ok(call(get(PRODUCTS + "/" + product + "/reference-price"), admin));
    }

    private static JsonNode block(JsonNode item, String key) {
        for (JsonNode b : item.path("blocks")) {
            if (key.equals(b.path("section").asText())) {
                return b;
            }
        }
        return null;
    }

    private static void money(String expected, JsonNode actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual.decimalValue()), expected + " vs " + actual);
    }

    @Test
    void import_success_withListingTableAlongside() throws Exception {
        String content = facts("2026-10-18", """
                ## 平台参考价
                | 价格类别 | 平台/网站 | 成色 | 参考价格（USD） | 来源原价 | 来源链接 |
                | --- | --- | --- | ---: | --- | --- |
                | 目标型号挂牌价 | eBay | New | 120.00 | USD 120.00 | [eBay](https://www.ebay.com/itm/1) |

                """ + card("USD", "300.00", "7.123456", "eBay listing 123456", "2026-10-19"));
        JsonNode p = preview(content);
        assertTrue(p.path("confirmable").asBoolean(), p.toString());
        JsonNode b = block(p, "CARD_PRICE");
        assertEquals(List.of("币种", "USD"), List.of(b.path("rows").get(0).get(0).asText(), b.path("rows").get(0).get(1).asText()));
        assertEquals("7.123456", b.path("rows").get(2).get(1).asText());
        assertTrue(b.path("hints").toString().contains("人民币参考价：2137.04"), b.toString());
        assertTrue(b.path("hints").toString().contains("写入商品卡片的平台参考价"));
        assertEquals(1, block(p, "LISTING_PRICES").path("count").asInt());
        assertTrue(block(p, "LISTING_PRICES").path("hints").toString().contains("商品卡片的平台参考价按「商品平台参考价」写入"));

        confirm(content);
        JsonNode r = price();
        assertEquals("USD", r.path("currencyCode").asText());
        money("300.00", r.path("priceOriginal"));
        money("7.123456", r.path("exchangeRate"));
        money("2137.04", r.path("priceCny"));
        assertEquals("eBay listing 123456", r.path("priceSource").asText());
        assertEquals("2026-10-19", r.path("priceDate").asText(), "表内日期优先于 updated");

        // 六列表格只进公开挂牌价，商品平台参考价不进挂牌价
        JsonNode listings = ok(call(get(PRODUCTS + "/" + product + "/listing-prices"), admin));
        assertEquals(1, listings.size());
        money("120.00", listings.get(0).path("priceUsd"));
    }

    @Test
    void listingTable_createsCardPrice_lowestNew_systemRate() throws Exception {
        jdbc.update("insert into exchange_rate (tenant_id, currency_code, rate) values (0, 'USD', 7.1)");
        String content = facts("2026-10-19", listings(
                "| 目标型号挂牌价 | eBay | New | 308.00 | USD 308.00 | [eBay](https://www.ebay.com/itm/1) |",
                "| 目标型号挂牌价 | eBay | Used | 250.00 | USD 250.00 | [eBay](https://www.ebay.com/itm/2) |",
                "| 目标型号挂牌价 | Amazon | New | 320.00 | USD 320.00 | [Amazon](https://www.amazon.com/dp/3) |"));
        JsonNode p = preview(content);
        assertTrue(p.path("confirmable").asBoolean(), p.toString());
        String hints = block(p, "LISTING_PRICES").path("hints").toString();
        assertTrue(hints.contains("同时生成商品卡片的平台参考价：取全新挂牌价里的最低价 USD 308.00"), hints);
        assertTrue(hints.contains("系统美元汇率 7.100000"), hints);
        assertTrue(hints.contains("人民币参考价：2186.80"), hints);

        confirm(content);
        JsonNode r = price();
        assertEquals("USD", r.path("currencyCode").asText());
        money("308.00", r.path("priceOriginal"));
        money("7.100000", r.path("exchangeRate"));
        money("2186.80", r.path("priceCny"));
        assertEquals("eBay New 目标型号挂牌价", r.path("priceSource").asText());
        assertEquals("2026-10-19", r.path("priceDate").asText());
        assertEquals(3, ok(call(get(PRODUCTS + "/" + product + "/listing-prices"), admin)).size(), "挂牌价照常整份写入");

        // 没有全新：取所有行的最低价；重新导入替换现有
        confirm(facts("2026-10-20", listings(
                "| 目标型号参考挂牌 | eBay（美国站） | Used | 300.00 | US$ 300.00 | [eBay](https://www.ebay.com/itm/4) |",
                "| 目标型号参考挂牌 | eBay（美国站） | Used | 289.69 | US$ 289.69 | [eBay](https://www.ebay.com/itm/5) |")));
        JsonNode used = price();
        money("289.69", used.path("priceOriginal"));
        money("2056.80", used.path("priceCny"));
        assertEquals("2026-10-20", used.path("priceDate").asText());
    }

    @Test
    void listingTable_noSystemRate_notCalculated_categoryRowsSkipped() throws Exception {
        // 类目参考行不当作本商品的参考价
        confirm(facts("2026-10-19", listings(
                "| 类目参考、不代表目标型号价格 | eBay | New | 99.00 | USD 99.00 | [eBay](https://www.ebay.com/itm/6) |")));
        assertFalse(price().has("currencyCode") && !price().path("currencyCode").isNull(), "只有类目参考时不生成");

        JsonNode p = preview(facts("2026-10-19", listings(
                "| 目标型号挂牌价 | eBay | New | 120.00 | USD 120.00 | [eBay](https://www.ebay.com/itm/7) |")));
        assertTrue(block(p, "LISTING_PRICES").path("hints").toString().contains("系统没有美元汇率"), p.toString());
        confirm(facts("2026-10-19", listings(
                "| 目标型号挂牌价 | eBay | New | 120.00 | USD 120.00 | [eBay](https://www.ebay.com/itm/7) |")));
        JsonNode r = price();
        money("120.00", r.path("priceOriginal"));
        assertTrue(r.path("priceCny").isNull() || r.path("priceCny").isMissingNode(), "没有汇率时未计算");
    }

    @Test
    void missingRate_dateFallsBackToUpdated_cny() throws Exception {
        confirm(facts("2026-10-18", card("EUR", "120", "—", "厂商目录价", null)));
        JsonNode r = price();
        assertEquals("EUR", r.path("currencyCode").asText());
        money("120.00", r.path("priceOriginal"));
        assertTrue(r.path("exchangeRate").isNull() || r.path("exchangeRate").isMissingNode(), "汇率缺失");
        assertTrue(r.path("priceCny").isNull() || r.path("priceCny").isMissingNode(), "未计算");
        assertEquals("2026-10-18", r.path("priceDate").asText(), "表内没有日期时取 updated");

        // 人民币：汇率按 1，表里写的汇率不用
        confirm(facts(null, card("CNY", "1,980.5", "7.2", "国内渠道价", "2026-10-20")));
        JsonNode cny = price();
        money("1.000000", cny.path("exchangeRate"));
        money("1980.50", cny.path("priceCny"));
    }

    @Test
    void unknownValues_doNotOverwrite() throws Exception {
        jdbc.update("insert into product_reference_price (tenant_id, product_id, price_original, currency_code, exchange_rate, price_cny, "
                + "price_source, price_date) values (0, ?, 280.00, 'USD', 7.100000, 1988.00, '厂商目录价', '2026-10-01')", product);
        JsonNode p = preview(facts(null, card("USD", "300.00", "[待确认]", "未知", null)));
        String hints = block(p, "CARD_PRICE").path("hints").toString();
        assertTrue(hints.contains("沿用现有 7.100000"), hints);
        assertTrue(hints.contains("保留现有「厂商目录价」"), hints);
        confirm(facts(null, card("USD", "300.00", "[待确认]", "未知", null)));
        JsonNode r = price();
        money("300.00", r.path("priceOriginal"));
        money("7.100000", r.path("exchangeRate"));
        money("2130.00", r.path("priceCny"));
        assertEquals("厂商目录价", r.path("priceSource").asText());
        assertEquals("2026-10-01", r.path("priceDate").asText());

        // 换了币种：原汇率不能沿用，变为未计算
        confirm(facts(null, card("EUR", "100", "", "未知", null)));
        JsonNode eur = price();
        assertTrue(eur.path("exchangeRate").isNull() || eur.path("exchangeRate").isMissingNode());
        assertEquals("厂商目录价", eur.path("priceSource").asText());

        // 币种和参考价都未知：整节不写入，已有值不变
        JsonNode none = preview(facts(null, "## 一句话规格摘要\nCP-100 PLC\n\n" + card("—", "未知", "7.1", "x", null)));
        assertTrue(none.path("confirmable").asBoolean());
        assertEquals(null, block(none, "CARD_PRICE"));
        assertTrue(none.path("notes").toString().contains("币种和参考价未知"), none.toString());
        assertEquals("EUR", price().path("currencyCode").asText());
    }

    @Test
    void invalidValues_rejected() throws Exception {
        assertError(card("US Dollar", "300", "7.1", "x", null), "ISO 4217");
        assertError(card("USD", "0", "7.1", "x", null), "参考价（原币）须大于 0");
        assertError(card("USD", "abc", "7.1", "x", null), "参考价（原币）须为大于 0 的数字");
        assertError(card("USD", "300", "-1", "x", null), "汇率须为大于 0 的数字");
        assertError(card("USD", "300", "0", "x", null), "汇率须大于 0");
        assertError(card("USD", "300", "7.1", "x", "2026/10/19"), "取价日期须为 YYYY-MM-DD");
        assertError(card("USD", "—", "7.1", "x", null), "要同时提供");
        assertFalse(price().has("currencyCode") && !price().path("currencyCode").isNull(), "无效时不写入");
    }

    private void assertError(String section, String expected) throws Exception {
        JsonNode p = preview(facts(null, section));
        assertFalse(p.path("confirmable").asBoolean(), p.toString());
        assertTrue(block(p, "CARD_PRICE").path("errors").toString().contains(expected), p.toString());
    }
}
