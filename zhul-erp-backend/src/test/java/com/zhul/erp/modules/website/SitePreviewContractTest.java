package com.zhul.erp.modules.website;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhul.erp.modules.media.service.MediaIndexer;
import com.zhul.erp.support.TenantContractSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * spec website-publish「发布预览与检查」：每一个阻断项、提醒项、上线链接、与上次发布的差异；
 * 批量预览时有阻断项的商品单独标出。
 */
class SitePreviewContractTest extends TenantContractSupport {

    private static final int A = 99751;
    private static final String BRAND = "ITPVBrand";
    private static final long CATEGORY = 5L;

    private long brand;
    private long p1;
    private long p2;
    private long siteId;
    private long t1;
    private long t2;
    private String a;

    @BeforeEach
    void seed() throws Exception {
        cleanup();
        jdbc.update("insert into product_brand (tenant_id, brand_name, status) values (0, ?, 1)", BRAND);
        brand = jdbc.queryForObject("select id from product_brand where brand_name = ?", Long.class, BRAND);
        p1 = product("PV-1");
        p2 = product("PV-2");
        loginAsAdmin("it_pv_admin");
        a = token("it_pv_admin", A);
        siteId = ok(call(json(post("/api/v1/websites"), write(Map.of("name", "Fouwell", "url", "https://it-pv.example.com",
                "publishMode", 2, "pathTemplate", "/products/{slug}/",
                "languages", List.of(Map.of("code", "en", "prefix", ""), Map.of("code", "ru", "prefix", "/ru")),
                "nonGenuineBrandIds", List.of(brand)))), a)).path("id").asLong();
        jdbc.update("insert into website_category_map (tenant_id, site_id, category_id, site_category) values (?, ?, ?, 'drives')",
                A, siteId, CATEGORY);
        for (long p : List.of(p1, p2)) {
            jdbc.update("insert into product_locale (tenant_id, product_id, lang, spec_summary) values (0, ?, 'en', 'Inverter')", p);
            for (String lang : List.of("en", "ru")) {
                jdbc.update("insert into product_seo (tenant_id, product_id, lang, seo_title, meta_description, geo_answer) "
                        + "values (?, ?, ?, 'Title', 'Meta', 'Overview')", A, p, lang);
            }
        }
        listingMedia("PV-1");
        listingMedia("PV-2");
        t1 = task(p1, "PV-1");
        t2 = task(p2, "PV-2");
    }

    @AfterEach
    void cleanup() {
        jdbc.update("delete from website_product where tenant_id = ?", A);
        jdbc.update("delete from website_category_map where tenant_id = ?", A);
        jdbc.update("delete from website_site where tenant_id = ?", A);
        jdbc.update("delete from listing_task where tenant_id = ?", A);
        jdbc.update("delete from product_seo where tenant_id = ?", A);
        jdbc.update("delete from media_item where tenant_id = ?", A);
        jdbc.update("delete from biz_attachment where tenant_id = ?", A);
        jdbc.update("delete from product_locale where product_id in (select id from product where brand_id in "
                + "(select id from product_brand where brand_name = ?))", BRAND);
        jdbc.update("delete from product where brand_id in (select id from product_brand where brand_name = ?)", BRAND);
        jdbc.update("delete from product_brand where brand_name = ?", BRAND);
    }

    @Test
    void complete_noBlockers_warnings_liveUrls_thenFieldChanges() throws Exception {
        JsonNode r = preview(t1).get(0);
        assertEquals(List.of(), texts(r.path("blockers")));
        assertTrue(r.path("publishable").asBoolean());
        assertEquals(List.of("没有售价（站点显示询价）", "没有视频", "品牌在非正品名单里（页面不出现正品说法）", "商品资料没有核对"),
                texts(r.path("warnings")));
        assertEquals("https://it-pv.example.com/products/itpvbrand-pv-1/", r.path("liveUrls").path("en").asText());
        assertEquals("https://it-pv.example.com/ru/products/itpvbrand-pv-1/", r.path("liveUrls").path("ru").asText());
        assertEquals("drives", r.path("siteCategory").asText());
        assertEquals("无（站点显示询价）", r.path("price").path("sourceName").asText());
        assertTrue(r.path("firstPublish").asBoolean());
        assertEquals(1, r.path("data").path("photos").size());
        assertFalse(r.path("data").has("release"));

        // 上次发布的快照里描述不同：只列出这一个字段
        JsonNode data = r.path("data").deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) data).put("meta_description", "Old meta");
        ((com.fasterxml.jackson.databind.node.ObjectNode) data).put("release", "WP10001");
        jdbc.update("update website_product set last_snapshot = ? where id = ?", data.toString(), r.path("websiteProductId").asLong());
        r = preview(t1).get(0);
        assertFalse(r.path("firstPublish").asBoolean());
        assertEquals(1, r.path("changes").size());
        assertEquals("meta_description", r.path("changes").get(0).path("field").asText());
        assertEquals("Old meta", r.path("changes").get(0).path("before").asText());
        assertEquals("Meta", r.path("changes").get(0).path("after").asText());
    }

    @Test
    void blockers_each() throws Exception {
        // 俄文缺少 SEO 描述（规格里的例子）、英文缺首屏定义块和标题
        jdbc.update("update product_seo set meta_description = '' where tenant_id = ? and product_id = ? and lang = 'ru'", A, p1);
        jdbc.update("update product_seo set geo_answer = '', seo_title = '' where tenant_id = ? and product_id = ? and lang = 'en'", A, p1);
        assertEquals(List.of("英文：缺少 SEO 标题", "英文：缺少首屏定义块", "俄文：缺少 SEO 描述"), blockers(t1));
        jdbc.update("update product_seo set meta_description = 'Meta', geo_answer = 'Overview', seo_title = 'T' where tenant_id = ?", A);

        // 品类没有对照
        jdbc.update("delete from website_category_map where tenant_id = ?", A);
        assertEquals(List.of("品类没有对照站点品类"), blockers(t1));
        jdbc.update("insert into website_category_map (tenant_id, site_id, category_id, site_category) values (?, ?, ?, 'drives')",
                A, siteId, CATEGORY);

        // 没有一句话规格
        jdbc.update("delete from product_locale where product_id = ?", p1);
        assertEquals(List.of("缺少一句话规格"), blockers(t1));
        jdbc.update("insert into product_locale (tenant_id, product_id, lang, spec_summary) values (0, ?, 'en', 'Inverter')", p1);

        // 没有任何图片
        jdbc.update("update media_item set for_listing = 0 where tenant_id = ? and model = 'PV-1'", A);
        assertEquals(List.of("没有任何图片"), blockers(t1));
        jdbc.update("update media_item set for_listing = 1 where tenant_id = ?", A);

        // 自动发布站点还没有以 ERP 为准
        jdbc.update("update website_site set publish_mode = 1 where id = ?", siteId);
        assertEquals(List.of("站点还没有切换为以 ERP 为准"), blockers(t1));
        jdbc.update("update website_site set takeover_status = 2 where id = ?", siteId);
        assertEquals(List.of(), blockers(t1));

        // 正在发布中
        jdbc.update("update listing_task set status = 4 where id = ?", t1);
        assertEquals(List.of("正在发布中"), blockers(t1));
        jdbc.update("update listing_task set status = 1 where id = ?", t1);

        // 已锁定的链接与重算的不一致
        jdbc.update("update website_product set slug_locked = 1 where tenant_id = ? and product_id = ?", A, p1);
        jdbc.update("update product set mpn_raw = 'PV-1B' where id = ?", p1);
        listingMedia("PV-1B");
        assertEquals(List.of("已发布商品的品牌、系列或型号改了，链接会变"), blockers(t1));
        jdbc.update("update product set mpn_raw = 'PV-1' where id = ?", p1);
        assertEquals(List.of(), blockers(t1));
    }

    @Test
    void slugConflict_inSiteAndInBatch_otherProductsStillPublishable() throws Exception {
        // p2 的型号改成与 p1 算出相同的链接：一起预览时两个都标冲突
        jdbc.update("update product set mpn_raw = 'PV 1' where id = ?", p2);
        listingMedia("PV 1");
        jdbc.update("update listing_task set asset_key = ? where id = ?", MediaIndexer.key(BRAND, "PV 1"), t2);
        JsonNode both = preview(t1, t2);
        assertEquals(List.of("链接别名在站点内冲突：itpvbrand-pv-1"), texts(both.get(0).path("blockers")));
        assertEquals(List.of("链接别名在站点内冲突：itpvbrand-pv-1"), texts(both.get(1).path("blockers")));
        // 站内已有（p1 已发布、链接锁定）：单独预览 p2 也冲突
        jdbc.update("update website_product set slug_locked = 1 where tenant_id = ? and product_id = ?", A, p1);
        assertEquals(List.of("链接别名在站点内冲突：itpvbrand-pv-1"), blockers(t2));

        // 批量预览跳过有问题的商品：p2 品类没有对照，p1 仍可发布
        jdbc.update("update product set mpn_raw = 'PV-2', category_id = 0 where id = ?", p2);
        both = preview(t1, t2);
        assertTrue(both.get(0).path("publishable").asBoolean());
        assertFalse(both.get(1).path("publishable").asBoolean());
        assertEquals(List.of("品类没有对照站点品类"), texts(both.get(1).path("blockers")));
    }

    @Test
    void rejects_cancelledOrOtherSiteTask_andNeedsPublishPermission() throws Exception {
        jdbc.update("update listing_task set status = 3 where id = ?", t1);
        fail(call(json(post("/api/v1/websites/" + siteId + "/preview"), write(Map.of("taskIds", List.of(t1)))), a));
        fail(call(json(post("/api/v1/websites/" + (siteId + 1000) + "/preview"), write(Map.of("taskIds", List.of(t2)))), a));
        fail(call(json(put("/api/v1/websites/products/1/price"), "{\"price\":1}"), token("it_pv_nobody", A)));
    }

    // ================================================================ 工具

    private JsonNode preview(long... taskIds) throws Exception {
        List<Long> ids = new ArrayList<>();
        for (long t : taskIds) {
            ids.add(t);
        }
        return ok(call(json(post("/api/v1/websites/" + siteId + "/preview"), write(Map.of("taskIds", ids))), a));
    }

    private List<String> blockers(long taskId) throws Exception {
        return texts(preview(taskId).get(0).path("blockers"));
    }

    private static List<String> texts(JsonNode arr) {
        List<String> out = new ArrayList<>();
        arr.forEach(n -> out.add(n.asText()));
        return out;
    }

    private long product(String mpn) {
        jdbc.update("insert into product (tenant_id, brand_id, mpn_raw, mpn_normalized, product_name, category_id) values (0, ?, ?, ?, ?, ?)",
                brand, mpn, mpn.toLowerCase() + System.nanoTime(), BRAND + " " + mpn, CATEGORY);
        return jdbc.queryForObject("select id from product where brand_id = ? and mpn_raw = ?", Long.class, brand, mpn);
    }

    private long task(long productId, String model) {
        jdbc.update("insert into listing_task (tenant_id, product_id, platform, site_id, asset_key, status) values (?, ?, 'site', ?, ?, 1)",
                A, productId, siteId, MediaIndexer.key(BRAND, model));
        return jdbc.queryForObject("select id from listing_task where tenant_id = ? and product_id = ?", Long.class, A, productId);
    }

    private void listingMedia(String model) {
        jdbc.update("insert into biz_attachment (tenant_id, owner_type, owner_id, kind, storage, file_key, url, file_name, file_size, content_type, uploaded_by) "
                + "values (?, 'SHOOT', 1, 1, 'LOCAL', 'tenant/1/shoot/202610/0123456789abcdef0123456789abcdef.png', '', 'a.png', 1, 'image/png', 0)", A);
        long att = jdbc.queryForObject("select max(id) from biz_attachment where tenant_id = ?", Long.class, A);
        jdbc.update("insert into media_item (tenant_id, asset_key, brand, model, category, kind, source_table, source_id, can_send, for_listing) "
                + "values (?, ?, ?, ?, 9, 1, 'biz_attachment', ?, 1, 1)", A, MediaIndexer.key(BRAND, model), BRAND, model, att);
    }
}
