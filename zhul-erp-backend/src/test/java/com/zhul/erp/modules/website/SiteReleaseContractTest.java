package com.zhul.erp.modules.website;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhul.erp.framework.storage.LocalFileStorage;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.media.service.MediaIndexer;
import com.zhul.erp.modules.website.service.SitePublisherClient;
import com.zhul.erp.modules.website.service.SiteReleaseCallback;
import com.zhul.erp.modules.website.service.SiteOutdatedJob;
import com.zhul.erp.modules.website.service.SiteOutdatedService;
import com.zhul.erp.modules.website.service.SiteReleaseJob;
import com.zhul.erp.support.TenantContractSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * tasks 7.1、7.2（spec website-publish「一键发布与发布单」）：生成发布单、全量站点数据、同站点排队、按步骤回调、
 * 成功写回上线链接和快照、失败任务退回、部署失败从部署一步重试、看门狗；回调在没有登录上下文的线程里处理。
 */
class SiteReleaseContractTest extends TenantContractSupport {

    private static final int A = 99771;
    private static final String BRAND = "ITRLBrand";
    private static final long CATEGORY = 5L;
    private static final String TOKEN = "it-publisher-token";
    private static final String PHOTO_KEY = "tenant/99771/shoot/202611/0123456789abcdef0123456789abcdef.png";
    private static final String COMMIT = "0123456789abcdef0123456789abcdef01234567";

    @MockBean
    private SitePublisherClient publisher;
    @Autowired
    private SiteReleaseJob job;
    @Autowired
    private SiteReleaseCallback callbacks;
    @Autowired
    private LocalFileStorage local;
    @Autowired
    private SiteOutdatedService outdated;
    @Autowired
    private SiteOutdatedJob outdatedJob;

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
        reset(publisher);
        when(publisher.enabled()).thenReturn(true);
        when(publisher.tokenMatches(anyString())).thenAnswer(i -> TOKEN.equals(i.getArgument(0)));
        when(publisher.submitJob(any())).thenReturn(new SitePublisherClient.Reply(202, null));
        Path png = Files.createTempFile("it-rl", ".png");
        Files.write(png, new byte[]{1, 2, 3});
        local.put(false, PHOTO_KEY, png, "image/png");
        Files.delete(png);
        jdbc.update("insert into product_brand (tenant_id, brand_name, status) values (0, ?, 1)", BRAND);
        brand = jdbc.queryForObject("select id from product_brand where brand_name = ?", Long.class, BRAND);
        p1 = product("RL-1");
        p2 = product("RL-2");
        loginAsAdmin("it_rl_admin");
        a = token("it_rl_admin", A);
        siteId = ok(call(json(post("/api/v1/websites"), write(Map.of("name", "Fouwell", "url", "https://it-rl.example.com",
                "publishMode", 2, "pathTemplate", "/products/{slug}/",
                "languages", List.of(Map.of("code", "en", "prefix", ""), Map.of("code", "ru", "prefix", "/ru"))))), a))
                .path("id").asLong();
        jdbc.update("update website_site set publish_mode = 1, takeover_status = 2, repo_url = 'https://github.com/x/site.git', "
                + "repo_branch = 'main', deploy_host = 'h', deploy_port = 22, deploy_user = 'u', deploy_path = '/www' where id = ?", siteId);
        jdbc.update("insert into website_category_map (tenant_id, site_id, category_id, site_category) values (?, ?, ?, 'drives')",
                A, siteId, CATEGORY);
        for (long p : List.of(p1, p2)) {
            jdbc.update("insert into product_locale (tenant_id, product_id, lang, spec_summary) values (0, ?, 'en', 'Inverter')", p);
            for (String lang : List.of("en", "ru")) {
                jdbc.update("insert into product_seo (tenant_id, product_id, lang, seo_title, meta_description, geo_answer) "
                        + "values (?, ?, ?, 'Title', 'Meta', 'Overview')", A, p, lang);
            }
        }
        officialPhoto(p1, "RL-1");
        officialPhoto(p2, "RL-2");
        t1 = task(p1, "RL-1");
        t2 = task(p2, "RL-2");
    }

    @AfterEach
    void cleanup() {
        for (String t : List.of("website_release_step", "website_release_item", "website_release", "website_site_job",
                "website_product", "website_category_map", "website_site", "listing_task", "product_seo", "media_item", "biz_attachment")) {
            jdbc.update("delete from " + t + " where tenant_id = ?", A);
        }
        for (String t : List.of("product_locale", "product_media", "product_specification")) {
            jdbc.update("delete from " + t + " where product_id in (select id from product where brand_id in "
                    + "(select id from product_brand where brand_name = ?))", BRAND);
        }
        jdbc.update("delete from product where brand_id in (select id from product_brand where brand_name = ?)", BRAND);
        jdbc.update("delete from product_brand where brand_name = ?", BRAND);
    }

    @Test
    void success_fullCatalog_stepCallbacks_writesBackLiveUrlsAndSnapshot() throws Exception {
        // 站点上已有一个发布过的商品和一个只在站点上有的商品：都要原样带上
        jdbc.update("insert into website_product (tenant_id, site_id, product_id, listing_task_id, slug, slug_locked, last_snapshot) "
                + "values (?, ?, ?, 0, 'old', 1, '{\"brand\":\"X\",\"model\":\"OLD-1\",\"release\":\"WP00001\"}')", A, siteId, p2);
        jdbc.update("insert into website_site_job (tenant_id, site_id, job_type, job_no, status, result) values (?, ?, 'import', 'IMP-1', 4, ?)",
                A, siteId, "{\"unmatched\":[{\"brand\":\"Y\",\"model\":\"ONLY-SITE\"}]}");

        JsonNode r = ok(call(json(post("/api/v1/websites/" + siteId + "/releases"), write(Map.of("taskIds", List.of(t1)))), a));
        String no = r.path("releaseNo").asText();
        assertTrue(no.startsWith("WP"));
        assertEquals("执行中", r.path("statusName").asText());
        assertEquals(4, taskStatus(t1));

        Map<String, Object> body = submitted();
        assertEquals(no + "-1", body.get("jobId"));
        assertEquals("publish", body.get("type"));
        assertTrue(String.valueOf(body.get("callbackUrl")).endsWith("/api/v1/website-publisher/callback"));
        JsonNode siteData = objectMapper.valueToTree(body.get("siteData"));
        List<String> models = new ArrayList<>();
        siteData.path("products").forEach(p -> models.add(p.path("model").asText()));
        assertEquals(List.of("OLD-1", "RL-1", "ONLY-SITE"), models, "按站点商品先后，最后是只在站点上有的");
        assertEquals(no, siteData.path("products").get(1).path("release").asText());
        assertEquals("WP00001", siteData.path("products").get(0).path("release").asText(), "没在这次发布里的保留原标记");
        JsonNode checks = objectMapper.valueToTree(body.get("checks"));
        assertEquals(2, checks.size());
        assertEquals("https://it-rl.example.com/products/itrlbrand-rl-1/", checks.get(0).path("url").asText());
        assertEquals("https://it-rl.example.com/ru/products/itrlbrand-rl-1/", checks.get(1).path("url").asText());
        assertEquals(1, checks.get(0).path("images").asInt());
        assertFalse(checks.get(0).path("video").asBoolean());
        assertFalse(body.containsKey("indexNowUrls"), "没配 IndexNow 密钥不推送");

        // 步骤回调（不带登录），再完成
        callback(Map.of("jobId", no + "-1", "event", "step", "step", "build", "status", "running", "startedAt", "2026-11-27T08:00:00.000Z"));
        callback(Map.of("jobId", no + "-1", "event", "step", "step", "build", "status", "success",
                "startedAt", "2026-11-27T08:00:00.000Z", "endedAt", "2026-11-27T08:01:00.000Z", "output", "built 150 pages"));
        callback(Map.of("jobId", no + "-1", "event", "done", "status", "success", "result", Map.of("commit", COMMIT,
                "warnings", List.of("线上检查没通过：…"),
                "online", List.of(Map.of("url", "https://it-rl.example.com/products/itrlbrand-rl-1/", "ok", false, "errors", List.of("旧页面"))))));

        JsonNode d = ok(call(get("/api/v1/websites/releases/" + r.path("id").asLong()), a));
        assertEquals("已完成", d.path("statusName").asText());
        assertEquals(COMMIT, d.path("commitSha").asText());
        assertEquals("线上检查没通过：…", d.path("warnings").get(0).asText());
        assertEquals("build", d.path("steps").get(0).path("step").asText());
        assertEquals(2, d.path("steps").get(0).path("status").asInt());
        assertEquals("2026-11-27T16:01:00", d.path("steps").get(0).path("endedAt").asText(), "存北京时间");
        assertEquals("旧页面", d.path("items").get(0).path("checkResult").get(0).path("errors").get(0).asText());

        assertEquals(5, taskStatus(t1));
        assertEquals("https://it-rl.example.com/products/itrlbrand-rl-1/",
                jdbc.queryForObject("select listing_url from listing_task where id = ?", String.class, t1));
        Map<String, Object> wp = jdbc.queryForMap("select slug_locked + 0 as slug_locked, last_snapshot, live_urls from website_product where tenant_id = ? and product_id = ?", A, p1);
        assertEquals(1, ((Number) wp.get("slug_locked")).intValue());
        assertEquals(no, objectMapper.readTree((String) wp.get("last_snapshot")).path("release").asText());
        assertEquals("https://it-rl.example.com/ru/products/itrlbrand-rl-1/",
                objectMapper.readTree((String) wp.get("live_urls")).path("ru").asText());
    }

    @Test
    void failure_tasksBackToPreviousStatus_staleCallbackIgnored() throws Exception {
        long rid = release(t1, t2);
        String no = releaseNo(rid);
        callback(Map.of("jobId", "WP-OTHER-1", "event", "done", "status", "success"));
        callback(Map.of("jobId", no + "-1", "event", "done", "status", "failed", "failedStep", "local_check",
                "error", "本地检查：A 英文页主图没有加载"));
        JsonNode d = ok(call(get("/api/v1/websites/releases/" + rid), a));
        assertEquals("失败", d.path("statusName").asText());
        assertEquals("local_check", d.path("failedStep").asText());
        assertEquals("本地检查：A 英文页主图没有加载", d.path("error").asText());
        assertEquals(1, taskStatus(t1));
        assertEquals(1, taskStatus(t2));
        assertNull(jdbc.queryForObject("select last_snapshot from website_product where tenant_id = ? and product_id = ?", String.class, A, p1));
        // 结束后再来的回调不改状态
        callback(Map.of("jobId", no + "-1", "event", "done", "status", "success", "result", Map.of()));
        assertEquals(4, jdbc.queryForObject("select status from website_release where id = ?", Integer.class, rid));
    }

    @Test
    void deployFailed_keepsPublishing_retryFromDeploy() throws Exception {
        long rid = release(t1);
        String no = releaseNo(rid);
        callback(Map.of("jobId", no + "-1", "event", "done", "status", "deploy_failed", "failedStep", "deploy",
                "error", "同步到部署服务器失败", "result", Map.of("commit", COMMIT)));
        assertEquals(5, jdbc.queryForObject("select status from website_release where id = ?", Integer.class, rid));
        assertEquals(4, taskStatus(t1), "线上可能只更新了一部分：保持发布中");

        JsonNode r = ok(call(post("/api/v1/websites/releases/" + rid + "/retry"), a));
        assertEquals("执行中", r.path("statusName").asText());
        Map<String, Object> body = submitted();
        assertEquals(no + "-2", body.get("jobId"));
        assertEquals("deploy", body.get("startFrom"));
        assertEquals(COMMIT, body.get("commit"));
        assertFalse(body.containsKey("siteData"));
        callback(Map.of("jobId", no + "-2", "event", "step", "step", "deploy", "status", "success"));
        callback(Map.of("jobId", no + "-2", "event", "done", "status", "success", "result", Map.of("commit", COMMIT)));
        assertEquals(5, taskStatus(t1));
        assertEquals(2, jdbc.queryForObject("select attempt from website_release_step where release_id = ?", Integer.class, rid));
        fail(call(post("/api/v1/websites/releases/" + rid + "/retry"), a));
    }

    @Test
    void sameSiteQueues_nextStartsAfterDone_busyRequeued_watchdog() throws Exception {
        long r1 = release(t1);
        long r2 = release(t2);
        assertEquals(2, releaseStatus(r1));
        assertEquals(1, releaseStatus(r2), "前一张执行中：排队");
        job.tick();
        assertEquals(1, releaseStatus(r2));

        callback(Map.of("jobId", releaseNo(r1) + "-1", "event", "done", "status", "success", "result", Map.of()));
        // 发布服务正忙（409）：退回排队
        when(publisher.submitJob(any())).thenReturn(new SitePublisherClient.Reply(409, null));
        job.tick();
        assertEquals(1, releaseStatus(r2));
        when(publisher.submitJob(any())).thenReturn(new SitePublisherClient.Reply(202, null));
        job.tick();
        assertEquals(2, releaseStatus(r2));
        assertEquals(releaseNo(r2) + "-1", submitted().get("jobId"));

        // 看门狗：执行超过 40 分钟标为失败，任务退回
        jdbc.update("update website_release set started_at = date_sub(now(), interval 41 minute) where id = ?", r2);
        job.tick();
        assertEquals(4, releaseStatus(r2));
        assertTrue(jdbc.queryForObject("select error from website_release where id = ?", String.class, r2).contains("40 分钟"));
        assertEquals(1, taskStatus(t2));
    }

    @Test
    void callback_tokenRequired_andHandledInThreadWithoutTenant() throws Exception {
        long rid = release(t1);
        String no = releaseNo(rid);
        mvc.perform(post("/api/v1/website-publisher/callback").contentType(MediaType.APPLICATION_JSON)
                        .header(SitePublisherClient.TOKEN_HEADER, "wrong").content("{\"jobId\":\"" + no + "-1\"}"))
                .andExpect(status().isUnauthorized());

        // 回调线程没有租户上下文
        AtomicReference<Throwable> error = new AtomicReference<>();
        Thread th = new Thread(() -> {
            try {
                TenantContext.clear();
                callbacks.handle(objectMapper.valueToTree(Map.of("jobId", no + "-1", "event", "done", "status", "failed",
                        "failedStep", "build", "error", "构建失败")));
            } catch (Throwable e) {
                error.set(e);
            }
        });
        th.start();
        th.join();
        assertNull(error.get());
        assertEquals(4, releaseStatus(rid));
        assertEquals(1, taskStatus(t1));
    }

    @Test
    void rejects_blockedProduct_andCopySite() throws Exception {
        jdbc.update("delete from website_category_map where tenant_id = ?", A);
        JsonNode err = fail(call(json(post("/api/v1/websites/" + siteId + "/releases"), write(Map.of("taskIds", List.of(t1)))), a));
        assertTrue(err.path("message").asText().contains("品类没有对照站点品类"));
        assertEquals(1, taskStatus(t1));
        jdbc.update("update website_site set publish_mode = 2 where id = ?", siteId);
        fail(call(json(post("/api/v1/websites/" + siteId + "/releases"), write(Map.of("taskIds", List.of(t1)))), a));
    }

    // ================================================================ 7.3 内容有更新

    @Test
    void outdated_eachTrigger_marksAndClears() throws Exception {
        long wp = published(t1);
        assertFalse(recompute(t1), "刚发布：没有更新");
        assertEquals(0, siteOutdated(t1));

        // 营销内容重新写入
        jdbc.update("update product_seo set geo_answer = 'Overview v2' where tenant_id = ? and product_id = ? and lang = 'en'", A, p1);
        assertTrue(recompute(t1));
        assertEquals(1, siteOutdated(t1));
        jdbc.update("update product_seo set geo_answer = 'Overview' where tenant_id = ? and product_id = ? and lang = 'en'", A, p1);
        assertFalse(recompute(t1), "改回去：标记清掉");
        assertEquals(0, siteOutdated(t1));

        // 商品资料（规格）
        jdbc.update("insert into product_specification (tenant_id, product_id, lang, spec_label, spec_value) values (0, ?, 'en', 'Power', '1.5 kW')", p1);
        assertTrue(recompute(t1));
        jdbc.update("delete from product_specification where product_id = ?", p1);
        assertFalse(recompute(t1));

        // 用于发品的素材
        jdbc.update("update media_item set for_listing = 0 where tenant_id = ? and model = 'RL-1'", A);
        assertTrue(recompute(t1));
        jdbc.update("update media_item set for_listing = 1 where tenant_id = ? and model = 'RL-1'", A);
        assertFalse(recompute(t1));

        // 售价（手工售价接口，事务提交后自动重算）
        ok(call(json(put("/api/v1/websites/products/" + wp + "/price"), write(Map.of("price", 105))), a));
        assertEquals(1, siteOutdated(t1));
        JsonNode pv = ok(call(json(post("/api/v1/websites/" + siteId + "/preview"), write(Map.of("taskIds", List.of(t1)))), a)).get(0);
        assertTrue(pv.path("changes").toString().contains("sell_price"));
        call(json(put("/api/v1/websites/products/" + wp + "/price"), "{\"price\":null}"), a);
        assertEquals(0, siteOutdated(t1));

        // 站点设置：品类对照改了（接口，自动重算）
        ok(call(json(put("/api/v1/websites/" + siteId + "/category-map"),
                write(Map.of("items", List.of(Map.of("categoryId", CATEGORY, "siteCategory", "motors"))))), a));
        assertEquals(1, siteOutdated(t1));
    }

    @Test
    void outdated_listTab_dailyJob_clearedByRepublish() throws Exception {
        published(t1);
        jdbc.update("update product_seo set meta_description = 'Meta v2' where tenant_id = ? and product_id = ? and lang = 'ru'", A, p1);
        assertEquals(0, siteOutdated(t1), "询价、汇率、直接改库这类变化不推送");
        outdatedJob.run();
        assertEquals(1, siteOutdated(t1));
        JsonNode list = ok(call(json(post("/api/v1/listing-tasks/page"), write(Map.of("tab", "updated", "page", 1, "pageSize", 20))), a));
        assertTrue(list.toString().contains("\"productId\":" + p1), "出现在「内容有更新」页签");

        // 同步到站点：发布成功后标记清掉
        long rid = release(t1);
        callback(Map.of("jobId", releaseNo(rid) + "-1", "event", "done", "status", "success", "result", Map.of()));
        assertEquals(5, taskStatus(t1));
        assertEquals(0, siteOutdated(t1));
        assertFalse(recompute(t1));
    }

    /** 发布并确认上线，返回站点商品 id */
    private long published(long task) throws Exception {
        long rid = release(task);
        callback(Map.of("jobId", releaseNo(rid) + "-1", "event", "done", "status", "success", "result", Map.of()));
        ok(call(json(post("/api/v1/listing-tasks/" + task + "/confirm-live"), "{\"purged\":true}"), a));
        assertEquals(2, taskStatus(task));
        return jdbc.queryForObject("select id from website_product where tenant_id = ? and listing_task_id = ?", Long.class, A, task);
    }

    /** 直接调用时按公司 A 的租户上下文 */
    private boolean recompute(long task) {
        java.util.concurrent.atomic.AtomicBoolean r = new java.util.concurrent.atomic.AtomicBoolean();
        TenantContext.runAs(A, () -> r.set(outdated.recompute(task)));
        return r.get();
    }

    private int siteOutdated(long task) {
        return jdbc.queryForObject("select site_outdated from listing_task where id = ?", Integer.class, task);
    }

    // ================================================================ 工具

    private long release(long... tasks) throws Exception {
        List<Long> ids = new ArrayList<>();
        for (long t : tasks) {
            ids.add(t);
        }
        return ok(call(json(post("/api/v1/websites/" + siteId + "/releases"), write(Map.of("taskIds", ids))), a)).path("id").asLong();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> submitted() {
        ArgumentCaptor<Map<String, Object>> c = ArgumentCaptor.forClass(Map.class);
        verify(publisher, atLeastOnce()).submitJob(c.capture());
        return c.getValue();
    }

    private void callback(Map<String, Object> body) throws Exception {
        mvc.perform(post("/api/v1/website-publisher/callback").contentType(MediaType.APPLICATION_JSON)
                        .header(SitePublisherClient.TOKEN_HEADER, TOKEN).content(write(body)))
                .andExpect(status().isOk());
    }

    private int taskStatus(long id) {
        return jdbc.queryForObject("select status from listing_task where id = ?", Integer.class, id);
    }

    private int releaseStatus(long id) {
        return jdbc.queryForObject("select status from website_release where id = ?", Integer.class, id);
    }

    private String releaseNo(long id) {
        return jdbc.queryForObject("select release_no from website_release where id = ?", String.class, id);
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

    /** 本公司的发品图片（与预览测试同一写法） */
    private void officialPhoto(long productId, String model) {
        jdbc.update("insert into biz_attachment (tenant_id, owner_type, owner_id, kind, storage, file_key, url, file_name, file_size, content_type, uploaded_by) "
                + "values (?, 'SHOOT', 1, 1, 'LOCAL', ?, '', 'a.png', 1, 'image/png', 0)", A, PHOTO_KEY);
        long att = jdbc.queryForObject("select max(id) from biz_attachment where tenant_id = ?", Long.class, A);
        jdbc.update("insert into media_item (tenant_id, asset_key, brand, model, category, kind, source_table, source_id, can_send, for_listing) "
                + "values (?, ?, ?, ?, 9, 1, 'biz_attachment', ?, 1, 1)", A, MediaIndexer.key(BRAND, model), BRAND, model, att);
    }
}
