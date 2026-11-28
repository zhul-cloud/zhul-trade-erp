package com.zhul.erp.modules.website;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.listing.entity.ListingTaskDO;
import com.zhul.erp.modules.listing.repository.ListingTaskMapper;
import com.zhul.erp.modules.website.entity.WebsiteProductDO;
import com.zhul.erp.modules.website.entity.WebsiteSiteDO;
import com.zhul.erp.modules.website.service.SiteDataService;
import com.zhul.erp.modules.website.service.SiteProductService;
import com.zhul.erp.modules.website.service.WebsiteSiteService;
import com.zhul.erp.support.TenantContractSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * spec website-publish「站点商品」：链接别名按站点约定生成、站内冲突、锁定后不变；
 * 售价按本公司近 180 天有货、已提交的最低回价计算（112.39 例子），没有回价用平台参考价，没有汇率时跳过询价；手工售价优先。
 */
class SiteProductContractTest extends TenantContractSupport {

    private static final int A = 99741;
    private static final int B = 99742;
    private static final String BRAND = "ITWPBrand";

    @Autowired
    private SiteProductService products;
    @Autowired
    private WebsiteSiteService sites;
    @Autowired
    private SiteDataService siteData;
    @Autowired
    private ListingTaskMapper listingTaskMapper;

    private long brand;
    private long series;
    private long p1;
    private long p2;
    private long siteId;
    private String a;

    @BeforeEach
    void seed() throws Exception {
        cleanup();
        jdbc.update("insert into product_brand (tenant_id, brand_name, status) values (0, ?, 1)", BRAND);
        brand = jdbc.queryForObject("select id from product_brand where brand_name = ?", Long.class, BRAND);
        jdbc.update("insert into product_series (tenant_id, brand_id, series_name, status) values (0, ?, 'FR-A800', 1)", brand);
        series = jdbc.queryForObject("select id from product_series where brand_id = ?", Long.class, brand);
        p1 = product("FR-A840-7.5K", series);
        p2 = product("FR A840 7.5K", series);
        loginAsAdmin("it_wp_admin");
        a = token("it_wp_admin", A);
        siteId = ok(call(json(post("/api/v1/websites"), write(Map.of("name", "Fouwell", "url", "https://it-wp.example.com",
                "publishMode", 2, "pathTemplate", "/products/{slug}/", "languages", List.of(Map.of("code", "en", "prefix", "")),
                "priceFactor", "1.05"))), a)).path("id").asLong();
    }

    @AfterEach
    void cleanup() {
        jdbc.update("delete from website_product where tenant_id in (?, ?)", A, B);
        jdbc.update("delete from listing_task where tenant_id in (?, ?)", A, B);
        jdbc.update("delete from product_seo where tenant_id in (?, ?)", A, B);
        jdbc.update("delete from product_seo_faq where tenant_id in (?, ?)", A, B);
        for (String t : List.of("product_locale", "product_specification", "product_relationship")) {
            jdbc.update("delete from " + t + " where product_id in (select id from product where brand_id in "
                    + "(select id from product_brand where brand_name = ?))", BRAND);
        }
        jdbc.update("delete from website_site where tenant_id in (?, ?)", A, B);
        jdbc.update("delete from sourcing_quote where tenant_id in (?, ?)", A, B);
        jdbc.update("delete from inquiry_item where tenant_id in (?, ?)", A, B);
        jdbc.update("delete from exchange_rate where tenant_id in (?, ?)", A, B);
        jdbc.update("delete from product_reference_price where product_id in (select id from product where brand_id in "
                + "(select id from product_brand where brand_name = ?))", BRAND);
        jdbc.update("delete from product where brand_id in (select id from product_brand where brand_name = ?)", BRAND);
        jdbc.update("delete from product_series where brand_id in (select id from product_brand where brand_name = ?)", BRAND);
        jdbc.update("delete from product_brand where brand_name = ?", BRAND);
    }

    @Test
    void price_lowestRecentInStockSubmittedQuote_specExample() {
        usdRate(A, "7.100000");
        quote(A, p1, "820.00", 2, 0, 10);
        quote(A, p1, "760.00", 2, 0, 20);
        quote(A, p1, "905.00", 2, 0, 30);
        quote(A, p1, "500.00", 2, 0, 200);  // 超过 180 天
        quote(A, p1, "300.00", 2, 1, 5);    // 无货
        quote(A, p1, "400.00", 4, 0, 5);    // 已作废
        quote(A, p1, "450.00", 1, 0, 5);    // 草稿
        quote(B, p1, "100.00", 2, 0, 5);    // 其他公司

        WebsiteProductDO wp = ensure(p1);
        assertEquals(new BigDecimal("112.39"), wp.getCalcPrice());
        JsonNode source = read(wp.getPriceSource());
        assertEquals("quote", source.path("type").asText());
        assertEquals("760.00", source.path("unitPriceCny").asText());
        assertEquals("1.05", source.path("factor").asText());
        assertEquals("7.100000", source.path("rate").asText());
        assertEquals("询价最低价", SiteProductService.sourceName("quote"));
    }

    @Test
    void price_noQuote_reference_noRate_manual() {
        // 没有回价、没有参考价：为空
        assertNull(ensure(p1).getCalcPrice());
        assertEquals("无（站点显示询价）", SiteProductService.sourceName(null));

        // 有回价但没有汇率：跳过询价，用美元参考价
        quote(A, p1, "760.00", 2, 0, 10);
        jdbc.update("insert into product_reference_price (tenant_id, product_id, price_original, currency_code, price_source) "
                + "values (0, ?, 215.50, 'USD', 'IT')", p1);
        WebsiteProductDO wp = ensure(p1);
        assertEquals(new BigDecimal("215.50"), wp.getCalcPrice());
        assertEquals("reference", read(wp.getPriceSource()).path("type").asText());

        // 手工售价优先；清空后回到计算
        TenantContext.runAs(A, () -> products.setManualPrice(wp.getId(), new BigDecimal("199.999")));
        assertEquals(new BigDecimal("200.00"), ensure(p1).getCalcPrice());
        TenantContext.runAs(A, () -> assertThrows(BizException.class, () -> products.setManualPrice(wp.getId(), BigDecimal.ZERO)));
        TenantContext.runAs(A, () -> products.setManualPrice(wp.getId(), null));
        assertEquals(new BigDecimal("215.50"), ensure(p1).getCalcPrice());
    }

    @Test
    void slug_fromBrandSeriesModel_conflict_lockedAfterPublish() {
        WebsiteProductDO one = ensure(p1);
        assertEquals("itwpbrand-fr-a800-fr-a840-7-5k", one.getSlug());
        WebsiteProductDO two = ensure(p2);
        assertEquals(one.getSlug(), two.getSlug(), "两个型号算出相同的链接");
        AtomicReference<Boolean> conflict = new AtomicReference<>();
        TenantContext.runAs(A, () -> conflict.set(products.slugConflict(siteId, two.getSlug(), two.getId())));
        assertFalse(conflict.get(), "都没发布过：不算站内冲突（同一批的重复由预览检查）");
        TenantContext.runAs(A, () -> products.lockSlug(one));
        TenantContext.runAs(A, () -> conflict.set(products.slugConflict(siteId, two.getSlug(), two.getId())));
        assertTrue(conflict.get(), "站内已有已发布的商品");

        // 锁定后改型号，链接不变；没锁定的跟着变
        jdbc.update("update product set mpn_raw = 'FR-A840-11K' where id = ?", p1);
        jdbc.update("update product set mpn_raw = 'FR-A840-15K' where id = ?", p2);
        assertEquals("itwpbrand-fr-a800-fr-a840-7-5k", ensure(p1).getSlug());
        assertEquals("itwpbrand-fr-a800-fr-a840-15k", ensure(p2).getSlug());
        TenantContext.runAs(A, () -> conflict.set(products.slugConflict(siteId, "itwpbrand-fr-a800-fr-a840-15k", ensure(p2).getId())));
        assertFalse(conflict.get());
    }

    @Test
    void siteData_erpContent_thenImportedKeepsSiteContentUntilUseErp() {
        jdbc.update("insert into product_locale (tenant_id, product_id, lang, spec_summary) values (0, ?, 'en', 'Inverter 7.5 kW')", p1);
        jdbc.update("insert into product_seo (tenant_id, product_id, lang, seo_title, meta_description, geo_answer) "
                + "values (?, ?, 'en', 'T', 'ERP meta', 'ERP overview')", A, p1);
        jdbc.update("insert into product_seo (tenant_id, product_id, lang, seo_title, meta_description, geo_answer) "
                + "values (?, ?, 'en', 'T', 'other company', 'other company')", B, p1);
        jdbc.update("insert into product_seo_faq (tenant_id, product_id, lang, question, answer) values (?, ?, 'ru', 'Вопрос?', 'Да')", A, p1);
        jdbc.update("insert into product_specification (tenant_id, product_id, lang, spec_key, spec_label, spec_value, spec_unit) "
                + "values (0, ?, 'en', 'power', 'Rated power', '7.5', 'kW')", p1);
        jdbc.update("insert into product_relationship (tenant_id, product_id, related_mpn, relationship_type, confidence, note) "
                + "values (0, ?, 'FR-A740-7.5K', 2, 2, 'Successor'), (0, ?, 'X-1', 3, 5, 'Maybe')", p1, p1);
        jdbc.update("insert into listing_task (tenant_id, product_id, platform, site_id, asset_key, status) values (?, ?, 'site', ?, '', 1)",
                A, p1, siteId);
        long taskId = jdbc.queryForObject("select id from listing_task where tenant_id = ? and product_id = ?", Long.class, A, p1);

        Map<String, Object> d = build(taskId);
        assertEquals("Inverter 7.5 kW", d.get("spec"));
        assertEquals("ERP overview", d.get("overview"));
        assertEquals("ERP meta", d.get("meta_description"));
        assertEquals(List.of(List.of("Rated power", "7.5 kW")), d.get("specs"));
        assertEquals(List.of(Map.of("from", "FR-A740-7.5K", "type", "successor", "note", "Successor"),
                Map.of("from", "X-1", "note", "Maybe")), d.get("compatibility"), "置信度低的不标类型");
        assertEquals(Map.of("faq", List.of(Map.of("q", "Вопрос?", "a", "Да"))), d.get("deep_ru"));
        assertEquals("itwpbrand-fr-a800-fr-a840-7-5k", d.get("slug"));
        assertEquals("WP10001", d.get("release"));
        assertEquals("instock", d.get("status"));
        assertFalse(d.containsKey("sell_price"));

        // 导入后（导入时间晚于 ERP 的全部更新）：用站点原有内容；点「改用 ERP 内容」后用 ERP
        WebsiteProductDO wp = ensure(p1);
        jdbc.update("update website_product set imported_at = ?, original_content = ? where id = ?", LocalDateTime.now().plusDays(1),
                "{\"brand\":\"ITWPBrand\",\"model\":\"FR-A840-7.5K\",\"spec\":\"site spec\",\"overview\":\"site overview\","
                        + "\"status\":\"legacy\",\"sell_price\":99.5,\"sell_price_currency\":\"USD\"}", wp.getId());
        d = build(taskId);
        assertEquals("site overview", d.get("overview"));
        assertEquals("site spec", d.get("spec"));
        assertEquals(99.5, ((Number) d.get("sell_price")).doubleValue());
        assertEquals("legacy", d.get("status"));
        TenantContext.runAs(A, () -> siteData.useErp(wp.getId(), List.of("content_en")));
        TenantContext.runAs(A, () -> products.setSiteStatus(wp.getId(), ""));
        d = build(taskId);
        assertEquals("ERP overview", d.get("overview"));
        assertEquals("instock", d.get("status"), "手工改状态后以 ERP 为准");
        assertEquals("site spec", d.get("spec"));
        TenantContext.runAs(A, () -> assertThrows(BizException.class, () -> siteData.useErp(wp.getId(), List.of("nope"))));
    }

    private Map<String, Object> build(long taskId) {
        AtomicReference<Map<String, Object>> out = new AtomicReference<>();
        TenantContext.runAs(A, () -> {
            WebsiteSiteDO site = sites.get(siteId);
            ListingTaskDO task = listingTaskMapper.selectById(taskId);
            WebsiteProductDO wp = products.ensure(site, task);
            out.set(siteData.build(site, task, wp, "WP10001", false).data());
        });
        return out.get();
    }

    // ================================================================ 工具

    private WebsiteProductDO ensure(long productId) {
        AtomicReference<WebsiteProductDO> out = new AtomicReference<>();
        TenantContext.runAs(A, () -> {
            WebsiteSiteDO site = sites.get(siteId);
            ListingTaskDO task = new ListingTaskDO();
            task.setId(0L);
            task.setProductId(productId);
            out.set(products.ensure(site, task));
        });
        return out.get();
    }

    private long product(String mpn, long seriesId) {
        jdbc.update("insert into product (tenant_id, brand_id, series_id, mpn_raw, mpn_normalized, product_name) values (0, ?, ?, ?, ?, ?)",
                brand, seriesId, mpn, mpn.replaceAll("[^A-Za-z0-9]", "").toLowerCase() + System.nanoTime(), BRAND + " " + mpn);
        return jdbc.queryForObject("select id from product where brand_id = ? and mpn_raw = ?", Long.class, brand, mpn);
    }

    private void usdRate(int tenantId, String rate) {
        jdbc.update("insert into exchange_rate (tenant_id, currency_code, rate, rate_time) values (?, 'USD', ?, now())", tenantId, new BigDecimal(rate));
    }

    private void quote(int tenantId, long productId, String cny, int status, int noStock, int daysAgo) {
        jdbc.update("insert into inquiry_item (tenant_id, product_id) values (?, ?)", tenantId, productId);
        long item = jdbc.queryForObject("select max(id) from inquiry_item where tenant_id = ?", Long.class, tenantId);
        jdbc.update("insert into sourcing_quote (tenant_id, inquiry_item_id, status, no_stock, unit_price, unit_price_cny, quoted_at) "
                + "values (?, ?, ?, ?, ?, ?, ?)", tenantId, item, status, noStock, new BigDecimal(cny), new BigDecimal(cny),
                LocalDateTime.now().minusDays(daysAgo));
    }

    private JsonNode read(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
