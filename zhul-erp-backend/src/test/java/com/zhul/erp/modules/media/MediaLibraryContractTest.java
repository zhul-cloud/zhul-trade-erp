package com.zhul.erp.modules.media;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhul.erp.support.TenantContractSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** 素材库接口：列表与筛选、详情分组与官方资料、两个标记的规则、链接权限、网络图片、参考素材的逐层回退与热度排序 */
class MediaLibraryContractTest extends TenantContractSupport {

    private static final int T = 99703;
    private static final String API = "/api/v1/media";
    private static final String BRAND = "ITMediaBrand";
    private static final int MENU = 100116;
    private static final int PERM_UPLOAD = 110211;
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 'I', 'H', 'D', 'R'};

    private long brand;
    private long series;
    private long category;

    @BeforeEach
    void seed() {
        cleanup();
        jdbc.update("insert into product_brand (tenant_id, brand_name, status) values (0, ?, 1)", BRAND);
        brand = jdbc.queryForObject("select id from product_brand where brand_name = ?", Long.class, BRAND);
        jdbc.update("insert into product_brand (tenant_id, brand_name, status) values (0, 'ITMediaOther', 1)");
        jdbc.update("insert into product_category (tenant_id, category_code, category_name, category_name_zh, status) "
                + "values (0, 'it-media-plc', 'PLC', 'PLC', 1)");
        category = jdbc.queryForObject("select id from product_category where category_code = 'it-media-plc'", Long.class);
        jdbc.update("insert into product_series (tenant_id, brand_id, series_name, status) values (0, ?, 'IT Series', 1)", brand);
        series = jdbc.queryForObject("select id from product_series where brand_id = ?", Long.class, brand);
    }

    @AfterEach
    void cleanup() {
        jdbc.update("delete from media_item where tenant_id in (?, 99704)", T);
        jdbc.update("delete from biz_attachment where tenant_id in (?, 99704)", T);
        jdbc.update("delete from inquiry_item where tenant_id = ?", T);
        jdbc.update("delete from product_media where product_id in (select id from product where brand_id in "
                + "(select id from product_brand where brand_name in (?, 'ITMediaOther')))", BRAND);
        jdbc.update("delete from product where brand_id in (select id from product_brand where brand_name in (?, 'ITMediaOther'))", BRAND);
        jdbc.update("delete from product_series where brand_id in (select id from product_brand where brand_name = ?)", BRAND);
        jdbc.update("delete from product_brand where brand_name in (?, 'ITMediaOther')", BRAND);
        jdbc.update("delete from product_category where category_code = 'it-media-plc'");
    }

    @Test
    void list_detail_flags_links_network() throws Exception {
        loginAsAdmin("it_media_admin");
        String admin = token("it_media_admin", T);
        product("M-1", series, category, brand);

        // 业务员上传一张网络图片：默认可发客户
        JsonNode net = ok(call(multipart(API + "/network").file(new MockMultipartFile("file", "网图.png", "image/png", PNG))
                .param("brand", BRAND).param("model", "M-1").param("note", "https://maker.example/m-1"), admin));
        assertEquals(5, net.path("category").asInt());
        assertTrue(net.path("canSend").asBoolean());
        long netItem = net.path("itemId").asLong();
        // 一张供方实拍（仅内部）、一条验货记录
        long quotePhoto = item("M-1", 4, 1, 0);
        long inspection = item("M-1", 7, 1, 1);

        JsonNode page = ok(call(json(post(API + "/models/page"), "{\"keyword\":\"M-1\"}"), admin));
        assertEquals(1, page.path("total").asInt());
        JsonNode row = page.path("records").get(0);
        assertEquals("M-1", row.path("model").asText());
        assertEquals(2, row.path("groupCounts").path("采购询价").asInt());
        assertEquals(1, row.path("groupCounts").path("到货验收").asInt());
        assertFalse(row.path("hasMarketing").asBoolean());
        assertEquals("ITMediaBrand M-1", row.path("productName").asText());
        assertEquals("PLC", row.path("category").asText());
        assertEquals("缺营销实拍", row.path("statusName").asText());
        assertEquals(0, ok(call(json(post(API + "/models/page"), "{\"hasMarketing\":true}"), admin)).path("total").asInt());

        JsonNode detail = ok(call(get(API + "/models/detail").param("brand", BRAND).param("model", "m-1"), admin));
        assertEquals(List.of("官方资料", "客户询盘", "采购询价", "到货验收", "营销拍摄", "仓库发货", "售后反馈"), names(detail.path("groups")));
        assertEquals(2, detail.path("groups").get(2).path("items").size());
        assertEquals("https://maker.example/m-1", detail.path("groups").get(2).path("items").findValues("note").stream()
                .map(JsonNode::asText).filter(s -> s.startsWith("https")).findFirst().orElse(""));
        assertTrue(detail.path("hasSendable").asBoolean());

        // 标记规则
        assertEquals("先改为可发客户才能用于发品", fail(flag(admin, quotePhoto, null, true)).path("message").asText());
        assertEquals("「验货记录」不能用于发品", fail(flag(admin, inspection, null, true)).path("message").asText());
        ok(flag(admin, quotePhoto, true, null));
        assertTrue(ok(flag(admin, quotePhoto, null, true)).path("forListing").asBoolean());
        assertEquals("listed", status(admin, "M-1"), "有用于发品的素材");
        JsonNode back = ok(flag(admin, quotePhoto, false, null));
        assertFalse(back.path("forListing").asBoolean(), "改回仅内部时同时取消用于发品");
        item("M-1", 9, 1, 1);
        assertEquals("marketing", status(admin, "M-1"), "有营销实拍、还没选发品素材");

        // 链接：仅内部的不能下载，预览可以
        assertEquals("仅内部的素材不能下载", fail(urls(admin, List.of(quotePhoto), true)).path("message").asText());
        assertEquals(1, ok(urls(admin, List.of(quotePhoto), false)).size());
        assertEquals(1, ok(urls(admin, List.of(netItem), true)).size());
        List<Long> many = new ArrayList<>();
        for (int i = 0; i < 51; i++) {
            many.add(netItem);
        }
        assertTrue(fail(urls(admin, many, false)).path("message").asText().contains("50"));

        // 其他公司看不到
        assertEquals(0, ok(call(json(post(API + "/models/page"), "{}"), token("it_media_admin", 99704))).path("total").asInt());

        // 只有查看与上传权限：不能改标记；不能删别人的网络图片
        loginWithResources("it_media_viewer", MENU, PERM_UPLOAD);
        String viewer = token("it_media_viewer", T);
        assertEquals(403, perform(json(post(API + "/flags"), write(Map.of("ref", Map.of("itemId", netItem), "canSend", false))), viewer).getStatus());
        jdbc.update("update biz_attachment set uploaded_by = 1 where id = (select source_id from media_item where id = ?)", netItem);
        assertEquals("只能删除自己上传的网络图片", fail(call(delete(API + "/network/" + netItem), viewer)).path("message").asText());
        loginAsAdmin("it_media_admin");
        ok(call(delete(API + "/network/" + netItem), admin));
        assertEquals(0, ok(call(get(API + "/models/detail").param("brand", BRAND).param("model", "M-1"), admin))
                .path("groups").get(2).path("items").findValues("note").stream().filter(n -> n.asText().startsWith("https")).count());
    }

    @Test
    void references_fallBackByLevel_sortedByHeat() throws Exception {
        loginAsAdmin("it_media_admin");
        String admin = token("it_media_admin", T);
        long self = product("R-0", series, category, brand);
        long sameSeriesA = product("R-1", series, category, brand);
        long sameSeriesB = product("R-2", series, category, brand);
        long sameBrandCat = product("R-3", null, category, brand);
        long other = jdbc.queryForObject("select id from product_brand where brand_name = 'ITMediaOther'", Long.class);
        long otherBrand = product("X-1", null, category, other);
        assertTrue(self > 0 && sameBrandCat > 0 && otherBrand > 0);

        // 同系列：R-1 有本公司的营销图，R-2 有官方图片；R-2 询盘更多，排在前面
        item("R-1", 9, 1, 1);
        jdbc.update("insert into product_media (tenant_id, product_id, media_type, file_url, storage_type, file_size) values (0, ?, 1, 'https://x/r2.jpg', 2, 0)",
                sameSeriesB);
        inquiry(sameSeriesB, 3);
        inquiry(sameSeriesA, 1);
        JsonNode r = ok(call(get(API + "/references").param("brand", BRAND).param("model", "R-0"), admin));
        assertEquals(1, r.path("level").asInt());
        assertEquals("同系列", r.path("levelName").asText());
        assertEquals(List.of("R-2", "R-1"), models(r));

        // 去掉同系列的素材 → 同品牌同品类（R-3）
        jdbc.update("delete from media_item where tenant_id = ? and asset_key = ?", T, "itmediabrand r-1");
        jdbc.update("delete from product_media where product_id = ?", sameSeriesB);
        item("R-3", 9, 1, 1);
        assertEquals(2, ok(call(get(API + "/references").param("brand", BRAND).param("model", "R-0"), admin)).path("level").asInt());

        // 本品牌都没有 → 不回退到同品类的其他品牌
        jdbc.update("delete from media_item where tenant_id = ? and asset_key = ?", T, "itmediabrand r-3");
        item("X-1", 9, 1, 1, "ITMediaOther");
        JsonNode none = ok(call(get(API + "/references").param("brand", BRAND).param("model", "R-0"), admin));
        assertEquals(0, none.path("level").asInt());
        assertEquals(List.of(), models(none));
    }

    // ---------------------------------------------------------------- 准备数据

    private long product(String mpn, Long seriesId, long categoryId, long brandId) {
        jdbc.update("insert into product (tenant_id, brand_id, category_id, series_id, mpn_raw, mpn_normalized, product_name) values (0, ?, ?, ?, ?, ?, ?)",
                brandId, categoryId, seriesId, mpn, mpn.replace("-", "").toLowerCase(), BRAND + " " + mpn);
        return jdbc.queryForObject("select id from product where brand_id = ? and mpn_raw = ?", Long.class, brandId, mpn);
    }

    private long item(String model, int category, int kind, int canSend) {
        return item(model, category, kind, canSend, BRAND);
    }

    /** 一条本公司素材：一个假附件（链接签发不读文件）+ 索引行 */
    private long item(String model, int category, int kind, int canSend, String brandName) {
        jdbc.update("insert into biz_attachment (tenant_id, owner_type, owner_id, kind, storage, file_key, url, file_name, file_size, content_type, uploaded_by) "
                + "values (?, 'SOURCING_QUOTE', 1, ?, 'LOCAL', 'tenant/1/sourcing/202610/0123456789abcdef0123456789abcdef.png', '', 'a.png', 1, 'image/png', 0)",
                T, kind);
        long att = jdbc.queryForObject("select max(id) from biz_attachment where tenant_id = ?", Long.class, T);
        jdbc.update("insert into media_item (tenant_id, asset_key, brand, model, category, kind, source_table, source_id, can_send) "
                + "values (?, ?, ?, ?, ?, ?, 'biz_attachment', ?, ?)", T, (brandName + " " + model).toLowerCase(), brandName, model, category, kind, att, canSend);
        return jdbc.queryForObject("select max(id) from media_item where tenant_id = ?", Long.class, T);
    }

    private void inquiry(long productId, int times) {
        for (int i = 0; i < times; i++) {
            jdbc.update("insert into inquiry_item (tenant_id, customer_inquiry_id, line_no, product_id, quantity) values (?, 0, ?, ?, 1)", T, i + 1, productId);
        }
    }

    private JsonNode flag(String token, long itemId, Boolean canSend, Boolean forListing) throws Exception {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("ref", Map.of("itemId", itemId));
        if (canSend != null) {
            body.put("canSend", canSend);
        }
        if (forListing != null) {
            body.put("forListing", forListing);
        }
        return call(json(post(API + "/flags"), write(body)), token);
    }

    private JsonNode urls(String token, List<Long> itemIds, boolean download) throws Exception {
        return call(json(post(API + "/urls"), write(Map.of("refs", itemIds.stream().map(i -> Map.of("itemId", i)).toList(),
                "download", download))), token);
    }

    private String status(String token, String model) throws Exception {
        return ok(call(json(post(API + "/models/page"), write(Map.of("keyword", model))), token))
                .path("records").get(0).path("status").asText();
    }

    private static List<String> names(JsonNode groups) {
        List<String> out = new ArrayList<>();
        groups.forEach(g -> out.add(g.path("name").asText()));
        return out;
    }

    private static List<String> models(JsonNode ref) {
        List<String> out = new ArrayList<>();
        ref.path("models").forEach(m -> out.add(m.path("model").asText()));
        return out;
    }
}
