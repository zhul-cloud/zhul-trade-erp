package com.zhul.erp.modules.website;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.website.service.SiteImportService;
import com.zhul.erp.modules.website.service.SitePublisherClient;
import com.zhul.erp.support.TenantContractSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * tasks 7.4（spec product/website-import）：导入（匹配、预览、确认后写入站点商品和发品任务、可以重复执行）
 * 与一致性比对（差异确认、开始以 ERP 为准、接管中禁止发布）。回调没有登录上下文。
 */
class SiteImportContractTest extends TenantContractSupport {

    private static final int A = 99772;
    private static final String BRAND = "ITIMBrand";
    private static final long CATEGORY = 5L;
    private static final String TOKEN = "it-import-token";

    @MockBean
    private SitePublisherClient publisher;
    @Autowired
    private SiteImportService imports;

    private long brand;
    private long p1;
    private long p2;
    private long siteId;
    private String a;

    @BeforeEach
    void seed() throws Exception {
        cleanup();
        reset(publisher);
        when(publisher.enabled()).thenReturn(true);
        when(publisher.tokenMatches(anyString())).thenAnswer(i -> TOKEN.equals(i.getArgument(0)));
        when(publisher.submitJob(any())).thenReturn(new SitePublisherClient.Reply(202, null));
        jdbc.update("insert into product_brand (tenant_id, brand_name, status) values (0, ?, 1)", BRAND);
        brand = jdbc.queryForObject("select id from product_brand where brand_name = ?", Long.class, BRAND);
        p1 = product("IM-1");
        p2 = product("IM-2");
        loginAsAdmin("it_im_admin");
        a = token("it_im_admin", A);
        siteId = ok(call(json(post("/api/v1/websites"), write(Map.of("name", "Fouwell", "url", "https://it-im.example.com",
                "publishMode", 2, "pathTemplate", "/products/{slug}/",
                "languages", List.of(Map.of("code", "en", "prefix", ""), Map.of("code", "ru", "prefix", "/ru"))))), a))
                .path("id").asLong();
        jdbc.update("update website_site set publish_mode = 1, takeover_status = 1, repo_url = 'https://github.com/x/site.git', "
                + "repo_branch = 'main', deploy_host = 'h', deploy_port = 22, deploy_user = 'u', deploy_path = '/www' where id = ?", siteId);
        jdbc.update("insert into website_category_map (tenant_id, site_id, category_id, site_category) values (?, ?, ?, 'drives')",
                A, siteId, CATEGORY);
    }

    @AfterEach
    void cleanup() {
        for (String t : List.of("website_site_job", "website_product", "website_category_map", "website_site", "listing_task")) {
            jdbc.update("delete from " + t + " where tenant_id = ?", A);
        }
        jdbc.update("delete from product where brand_id in (select id from product_brand where brand_name = ?)", BRAND);
        jdbc.update("delete from product_brand where brand_name = ?", BRAND);
    }

    @Test
    void import_matchPreviewConfirm_thenRepeat() throws Exception {
        JsonNode job = ok(call(post("/api/v1/websites/" + siteId + "/import"), a));
        assertEquals("执行中", job.path("statusName").asText());
        Map<String, Object> body = submitted();
        assertEquals("import", body.get("type"));
        assertFalse(body.containsKey("siteData"));
        fail(call(post("/api/v1/websites/" + siteId + "/compare"), a));   // 同一站点同时只有一个

        // 品牌、型号大小写和首尾空格不同也能匹配；X-9 没有档案；authenticity_note 是站点独有字段
        Map<String, Object> im1 = siteProduct(" itimbrand ", "im-1 ", "itimbrand-im-1", "Overview A");
        im1.put("authenticity_note", "Genuine");
        done(job, Map.of("exported", Map.of("products", List.of(im1, siteProduct(BRAND, "X-9", "itimbrand-x-9", "X")))));

        JsonNode d = ok(call(get("/api/v1/websites/jobs/" + job.path("id").asLong()), a));
        assertEquals("已完成", d.path("statusName").asText());
        JsonNode r = d.path("result");
        assertEquals(2, r.path("total").asInt());
        assertEquals(1, r.path("matched").size());
        assertEquals(p1, r.path("matched").get(0).path("productId").asLong());
        assertEquals(1, r.path("matched").get(0).path("photos").asInt());
        assertEquals(BRAND + " IM-1", r.path("matched").get(0).path("name").asText(), "已匹配带商品名称");
        assertTrue(r.path("matched").get(0).path("diffFields").toString().contains("overview"), "站点内容与 ERP 不同的字段");
        assertEquals("X-9", r.path("unmatched").get(0).path("model").asText());
        assertTrue(r.path("siteOnlyFields").toString().contains("authenticity_note"));
        assertEquals(0, jdbc.queryForObject("select count(*) from website_product where tenant_id = ?", Integer.class, A), "确认前不写入");

        ok(call(post("/api/v1/websites/jobs/" + job.path("id").asLong() + "/confirm-import"), a));
        Map<String, Object> wp = jdbc.queryForMap("select slug, slug_locked + 0 as locked, original_content, last_snapshot, imported_at, live_urls "
                + "from website_product where tenant_id = ? and product_id = ?", A, p1);
        assertEquals("itimbrand-im-1", wp.get("slug"));
        assertEquals(1, ((Number) wp.get("locked")).intValue());
        assertEquals("Genuine", objectMapper.readTree((String) wp.get("original_content")).path("authenticity_note").asText());
        assertNotNull(wp.get("imported_at"));
        assertEquals("https://it-im.example.com/ru/products/itimbrand-im-1/",
                objectMapper.readTree((String) wp.get("live_urls")).path("ru").asText());
        Map<String, Object> task = jdbc.queryForMap("select status, listing_url, published_by from listing_task where tenant_id = ? and product_id = ?", A, p1);
        assertEquals(2, ((Number) task.get("status")).intValue());
        assertEquals("https://it-im.example.com/products/itimbrand-im-1/", task.get("listing_url"));
        assertEquals("it_im_admin", task.get("published_by"));
        fail(call(post("/api/v1/websites/jobs/" + job.path("id").asLong() + "/confirm-import"), a));

        // 接管中：预览阻断；站点独有字段在发布数据里原样带上
        long t1 = jdbc.queryForObject("select id from listing_task where tenant_id = ? and product_id = ?", Long.class, A, p1);
        JsonNode pv = ok(call(json(post("/api/v1/websites/" + siteId + "/preview"), write(Map.of("taskIds", List.of(t1)))), a)).get(0);
        assertTrue(pv.path("blockers").toString().contains("站点还没有切换为以 ERP 为准"));
        assertEquals("Genuine", pv.path("data").path("authenticity_note").asText());
        assertEquals("Overview A", pv.path("data").path("overview").asText(), "导入后 ERP 没改过：用站点原有内容");

        // 重复导入：A 用站点最新内容覆盖，新匹配的 B 补充导入
        JsonNode job2 = ok(call(post("/api/v1/websites/" + siteId + "/import"), a));
        done(job2, Map.of("exported", Map.of("products", List.of(siteProduct(BRAND, "IM-1", "itimbrand-im-1", "Overview A2"),
                siteProduct(BRAND, "IM-2", "itimbrand-im-2", "Overview B")))));
        ok(call(post("/api/v1/websites/jobs/" + job2.path("id").asLong() + "/confirm-import"), a));
        assertEquals("Overview A2", objectMapper.readTree(jdbc.queryForObject(
                "select original_content from website_product where tenant_id = ? and product_id = ?", String.class, A, p1)).path("overview").asText());
        assertEquals(2, jdbc.queryForObject("select count(*) from website_product where tenant_id = ?", Integer.class, A));
        assertEquals(1, jdbc.queryForObject("select count(*) from listing_task where tenant_id = ? and product_id = ?", Integer.class, A, p1));

        // 站点上已经没有的商品不删除，列出来
        JsonNode job3 = ok(call(post("/api/v1/websites/" + siteId + "/import"), a));
        done(job3, Map.of("exported", Map.of("products", List.of(siteProduct(BRAND, "IM-2", "itimbrand-im-2", "Overview B")))));
        JsonNode r3 = ok(call(get("/api/v1/websites/jobs/" + job3.path("id").asLong()), a)).path("result");
        assertEquals("IM-1", r3.path("missing").get(0).path("model").asText());
        assertEquals(2, jdbc.queryForObject("select count(*) from website_product where tenant_id = ? and deleted_at is null", Integer.class, A));
        // 旧的导入结果不能再确认
        fail(call(post("/api/v1/websites/jobs/" + job2.path("id").asLong() + "/confirm-import"), a));
    }

    @Test
    void compare_acceptDifferences_thenTakeover() throws Exception {
        JsonNode imp = ok(call(post("/api/v1/websites/" + siteId + "/import"), a));
        done(imp, Map.of("exported", Map.of("products", List.of(siteProduct(BRAND, "IM-1", "itimbrand-im-1", "Overview A"),
                siteProduct(BRAND, "X-9", "itimbrand-x-9", "X")))));
        ok(call(post("/api/v1/websites/jobs/" + imp.path("id").asLong() + "/confirm-import"), a));

        JsonNode err = fail(call(post("/api/v1/websites/" + siteId + "/takeover"), a));
        assertTrue(err.path("message").asText().contains("一致性比对"));

        JsonNode cmp = ok(call(post("/api/v1/websites/" + siteId + "/compare"), a));
        Map<String, Object> body = submitted();
        assertEquals("compare", body.get("type"));
        JsonNode products = objectMapper.valueToTree(body.get("siteData")).path("products");
        List<String> models = new ArrayList<>();
        products.forEach(p -> models.add(p.path("model").asText()));
        assertEquals(List.of("IM-1", "X-9"), models, "ERP 管理的商品 + 只在站点上有的商品");
        assertEquals("Overview A", products.get(0).path("overview").asText());

        done(cmp, Map.of("same", 0, "onlyOnSite", List.of("X-9"), "onlyInErp", List.of(), "differences", List.of(
                Map.of("model", "IM-1", "field", "sell_price", "site", 246.10, "erp", 246.1),
                Map.of("model", "IM-1", "field", "spec", "site", "A", "erp", "B"))));
        JsonNode c = ok(call(get("/api/v1/websites/jobs/" + cmp.path("id").asLong()), a));
        assertEquals(2, c.path("pending").asInt());
        assertEquals(BRAND, c.path("result").path("brands").path("IM-1").asText(), "差异行显示品牌：取商品档案");
        assertEquals(BRAND, c.path("result").path("brands").path("X-9").asText(), "只在站点上有：取导入时站点上的品牌");
        err = fail(call(post("/api/v1/websites/" + siteId + "/takeover"), a));
        assertEquals("还有 2 条差异没有确认", err.path("message").asText());
        JsonNode summary = ok(call(get("/api/v1/websites/" + siteId), a)).path("summary");
        assertEquals(2, summary.path("pendingDiffs").asInt(), "站点管理显示未确认差异");
        assertEquals(1, summary.path("importedCount").asInt());

        fail(call(json(post("/api/v1/websites/jobs/" + cmp.path("id").asLong() + "/accept"),
                write(Map.of("items", List.of(Map.of("model", "IM-1", "field", "sell_price")), "reason", " "))), a));
        c = ok(call(json(post("/api/v1/websites/jobs/" + cmp.path("id").asLong() + "/accept"),
                write(Map.of("items", List.of(Map.of("model", "IM-1", "field", "sell_price")), "reason", "只是小数位写法不同"))), a));
        assertEquals(1, c.path("pending").asInt());
        assertEquals("it_im_admin", c.path("accepted").get(0).path("by").asText());
        err = fail(call(post("/api/v1/websites/" + siteId + "/takeover"), a));
        assertEquals("还有 1 条差异没有确认", err.path("message").asText());
        c = ok(call(json(post("/api/v1/websites/jobs/" + cmp.path("id").asLong() + "/accept"),
                write(Map.of("reason", "以 ERP 为准"))), a));
        assertEquals(0, c.path("pending").asInt());

        JsonNode site = ok(call(post("/api/v1/websites/" + siteId + "/takeover"), a));
        assertEquals(2, site.path("takeoverStatus").asInt());
        assertEquals("it_im_admin", site.path("takeoverBy").asText());
        // 以 ERP 为准之后不能再导入、比对，也不能退回
        fail(call(post("/api/v1/websites/" + siteId + "/import"), a));
        fail(call(post("/api/v1/websites/" + siteId + "/takeover"), a));
    }

    @Test
    void emptySite_takeoverDirectly_failedJob_watchdog_callbackWithoutTenant() throws Exception {
        // 发布服务拒绝：任务失败
        when(publisher.submitJob(any())).thenReturn(new SitePublisherClient.Reply(409, null));
        fail(call(post("/api/v1/websites/" + siteId + "/import"), a));
        assertEquals(3, jdbc.queryForObject("select status from website_site_job where tenant_id = ? order by id desc limit 1", Integer.class, A));
        when(publisher.submitJob(any())).thenReturn(new SitePublisherClient.Reply(202, null));

        // 回调线程没有租户上下文；失败回调记原因
        JsonNode job = ok(call(post("/api/v1/websites/" + siteId + "/import"), a));
        AtomicReference<Throwable> error = new AtomicReference<>();
        Thread th = new Thread(() -> {
            try {
                TenantContext.clear();
                imports.handle(objectMapper.valueToTree(Map.of("jobId", job.path("jobNo").asText(), "event", "done",
                        "status", "failed", "error", "导出现有商品失败")));
            } catch (Throwable e) {
                error.set(e);
            }
        });
        th.start();
        th.join();
        assertNull(error.get());
        JsonNode d = ok(call(get("/api/v1/websites/jobs/" + job.path("id").asLong()), a));
        assertEquals("失败", d.path("statusName").asText());
        assertEquals("导出现有商品失败", d.path("error").asText());

        // 看门狗：执行超过 40 分钟标为失败
        JsonNode job2 = ok(call(post("/api/v1/websites/" + siteId + "/compare"), a));
        jdbc.update("update website_site_job set started_at = date_sub(now(), interval 41 minute) where id = ?", job2.path("id").asLong());
        com.zhul.erp.framework.tenant.TenantScope.platform(() -> imports.watchdog(java.time.LocalDateTime.now()));
        assertEquals(3, jdbc.queryForObject("select status from website_site_job where id = ?", Integer.class, job2.path("id").asLong()));

        // 仓库里本来没有商品：可以直接开始以 ERP 为准
        assertEquals(2, ok(call(post("/api/v1/websites/" + siteId + "/takeover"), a)).path("takeoverStatus").asInt());
        assertEquals(3, ok(call(get("/api/v1/websites/" + siteId + "/jobs"), a)).size());
    }

    // ================================================================ 工具

    private void done(JsonNode job, Map<String, Object> result) throws Exception {
        mvc.perform(post("/api/v1/website-publisher/callback").contentType(MediaType.APPLICATION_JSON)
                        .header(SitePublisherClient.TOKEN_HEADER, TOKEN)
                        .content(write(Map.of("jobId", job.path("jobNo").asText(), "event", "done", "status", "success", "result", result))))
                .andExpect(status().isOk());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> submitted() {
        ArgumentCaptor<Map<String, Object>> c = ArgumentCaptor.forClass(Map.class);
        verify(publisher, atLeastOnce()).submitJob(c.capture());
        return c.getValue();
    }

    private static Map<String, Object> siteProduct(String brand, String model, String slug, String overview) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("brand", brand);
        m.put("model", model);
        m.put("series", "");
        m.put("cat", "drives");
        m.put("slug", slug);
        m.put("spec", "Inverter");
        m.put("overview", overview);
        m.put("sell_price", 246.10);
        m.put("photos", List.of(Map.of("src", "/images/products/" + slug + ".jpg", "tag", "Photo")));
        m.put("videos", List.of());
        return m;
    }

    private long product(String mpn) {
        jdbc.update("insert into product (tenant_id, brand_id, mpn_raw, mpn_normalized, product_name, category_id, archive_status) "
                + "values (0, ?, ?, ?, ?, ?, 2)", brand, mpn, mpn.toLowerCase() + System.nanoTime(), BRAND + " " + mpn, CATEGORY);
        return jdbc.queryForObject("select id from product where brand_id = ? and mpn_raw = ?", Long.class, brand, mpn);
    }
}
