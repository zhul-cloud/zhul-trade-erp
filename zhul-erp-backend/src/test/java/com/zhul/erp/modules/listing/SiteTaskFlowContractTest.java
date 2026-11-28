package com.zhul.erp.modules.listing;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.listing.service.SiteTaskFlow;
import com.zhul.erp.support.TenantContractSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * spec listing-task「独立站一键发布的任务状态」：状态机的合法与非法流转、确认上线要勾选清缓存、
 * 自动发布站点不能手工标记已发布或已同步、「待确认上线」页签。
 */
class SiteTaskFlowContractTest extends TenantContractSupport {

    private static final int A = 99761;
    private static final String BRAND = "ITSTBrand";

    @Autowired
    private SiteTaskFlow flow;

    private long product;
    private long siteId;
    private long task;
    private String a;

    @BeforeEach
    void seed() throws Exception {
        cleanup();
        jdbc.update("insert into product_brand (tenant_id, brand_name, status) values (0, ?, 1)", BRAND);
        long brand = jdbc.queryForObject("select id from product_brand where brand_name = ?", Long.class, BRAND);
        jdbc.update("insert into product (tenant_id, brand_id, mpn_raw, mpn_normalized, product_name) values (0, ?, 'ST-1', ?, 'ST-1')",
                brand, "st1" + System.nanoTime());
        product = jdbc.queryForObject("select id from product where brand_id = ?", Long.class, brand);
        loginAsAdmin("it_st_admin");
        a = token("it_st_admin", A);
        siteId = ok(call(json(post("/api/v1/websites"), write(Map.of("name", "Fouwell", "url", "https://it-st.example.com",
                "publishMode", 2, "pathTemplate", "/products/{slug}/", "languages", List.of(Map.of("code", "en", "prefix", ""))))), a))
                .path("id").asLong();
        // 改成自动发布、需要人工清缓存（建站时自动发布要仓库凭据，这里直接改库）
        jdbc.update("update website_site set publish_mode = 1, need_purge = 1, purge_note = '登录 SiteGround 清除动态缓存' where id = ?", siteId);
        jdbc.update("insert into listing_task (tenant_id, product_id, platform, site_id, asset_key, status) values (?, ?, 'site', ?, '', 1)",
                A, product, siteId);
        task = jdbc.queryForObject("select id from listing_task where tenant_id = ? and product_id = ?", Long.class, A, product);
    }

    @AfterEach
    void cleanup() {
        jdbc.update("delete from listing_task where tenant_id = ?", A);
        jdbc.update("delete from website_product where tenant_id = ?", A);
        jdbc.update("delete from website_site where tenant_id = ?", A);
        jdbc.update("delete from product where brand_id in (select id from product_brand where brand_name = ?)", BRAND);
        jdbc.update("delete from product_brand where brand_name = ?", BRAND);
    }

    @Test
    void legal_pending_publishing_unconfirmed_confirmed_thenRepublishAndFailBack() throws Exception {
        assertEquals(1, value(() -> flow.begin(task, 501L)));
        assertEquals(4, status());
        run(() -> flow.succeed(task, 501L, "https://it-st.example.com/products/itstbrand-st-1/", "it_st_admin"));
        assertEquals(5, status());
        assertEquals("https://it-st.example.com/products/itstbrand-st-1/",
                jdbc.queryForObject("select listing_url from listing_task where id = ?", String.class, task));

        // 详情：清缓存说明、上线链接
        jdbc.update("insert into website_product (tenant_id, site_id, product_id, listing_task_id, slug, live_urls) values (?, ?, ?, ?, 'itstbrand-st-1', ?)",
                A, siteId, product, task, "{\"en\":\"https://it-st.example.com/products/itstbrand-st-1/\"}");
        JsonNode d = ok(call(get("/api/v1/listing-tasks/" + task), a));
        assertEquals("已上线待确认", d.path("task").path("statusName").asText());
        assertEquals(501L, d.path("task").path("releaseId").asLong());
        assertTrue(d.path("site").path("autoPublish").asBoolean());
        assertEquals("登录 SiteGround 清除动态缓存", d.path("site").path("purgeNote").asText());
        assertEquals("https://it-st.example.com/products/itstbrand-st-1/", d.path("site").path("liveUrls").path("en").asText());

        // 确认上线：没勾选已清缓存被拒绝，勾选后为已发布
        JsonNode err = fail(call(json(post("/api/v1/listing-tasks/" + task + "/confirm-live"), "{\"purged\":false}"), a));
        assertEquals("请先按说明清理站点缓存，再勾选『已清缓存』", err.path("message").asText());
        d = ok(call(json(post("/api/v1/listing-tasks/" + task + "/confirm-live"), "{\"purged\":true}"), a));
        assertEquals(2, d.path("task").path("status").asInt());
        assertEquals("it_st_admin", d.path("task").path("confirmedBy").asText());

        // 已发布的再发布（同步更新），失败回到已发布
        assertEquals(2, value(() -> flow.begin(task, 502L)));
        run(() -> flow.fail(task, 502L, 2));
        assertEquals(2, status());
        assertEquals(502L, jdbc.queryForObject("select release_id from listing_task where id = ?", Long.class, task));
    }

    @Test
    void illegal_transitions_rejected() {
        // 待发布不能确认上线、不能完成或失败
        assertThrows(BizException.class, () -> run(() -> flow.confirm(task, true)));
        assertThrows(BizException.class, () -> run(() -> flow.succeed(task, 501L, "", "x")));
        assertThrows(BizException.class, () -> run(() -> flow.fail(task, 501L, 1)));
        // 发布中不能再发起；别的发布单不能完成它
        value(() -> flow.begin(task, 501L));
        assertThrows(BizException.class, () -> value(() -> flow.begin(task, 502L)));
        assertThrows(BizException.class, () -> run(() -> flow.succeed(task, 999L, "", "x")));
        assertThrows(BizException.class, () -> run(() -> flow.confirm(task, true)));
        assertEquals(4, status());
        // 已取消不能发起
        jdbc.update("update listing_task set status = 3 where id = ?", task);
        assertThrows(BizException.class, () -> value(() -> flow.begin(task, 503L)));
        // 只复制的站点不能一键发布
        jdbc.update("update listing_task set status = 1 where id = ?", task);
        jdbc.update("update website_site set publish_mode = 2 where id = ?", siteId);
        assertThrows(BizException.class, () -> value(() -> flow.begin(task, 504L)));
        assertEquals(1, status());
    }

    @Test
    void autoSite_noManualPublishOrSync_copySiteStillManual() throws Exception {
        JsonNode err = fail(call(json(post("/api/v1/listing-tasks/" + task + "/publish"),
                write(Map.of("listingUrl", "https://it-st.example.com/products/x/"))), a));
        assertEquals("这个站点通过一键发布上线", err.path("message").asText());
        jdbc.update("update listing_task set status = 2 where id = ?", task);
        err = fail(call(post("/api/v1/listing-tasks/" + task + "/sync"), a));
        assertEquals("这个站点通过「同步到站点」更新", err.path("message").asText());

        // 只复制的站点照常手工标记（素材待补的检查仍在）
        jdbc.update("update listing_task set status = 1 where id = ?", task);
        jdbc.update("update website_site set publish_mode = 2 where id = ?", siteId);
        err = fail(call(json(post("/api/v1/listing-tasks/" + task + "/publish"),
                write(Map.of("listingUrl", "https://it-st.example.com/products/x/"))), a));
        assertEquals("还没有发品素材，先补素材或选择参考素材", err.path("message").asText());
    }

    @Test
    void tabs_publishingCountsAsPending_unconfirmedTab() throws Exception {
        value(() -> flow.begin(task, 501L));
        JsonNode c = ok(call(json(post("/api/v1/listing-tasks/counts"), "{}"), a));
        assertEquals(1, c.path("pending").asInt());
        assertEquals(0, c.path("unconfirmed").asInt());
        run(() -> flow.succeed(task, 501L, "", "x"));
        c = ok(call(json(post("/api/v1/listing-tasks/counts"), "{}"), a));
        assertEquals(0, c.path("pending").asInt());
        assertEquals(1, c.path("unconfirmed").asInt());
        assertEquals(0, c.path("published").asInt());
        JsonNode rows = ok(call(json(post("/api/v1/listing-tasks/page"), "{\"tab\":\"unconfirmed\"}"), a)).path("records");
        assertEquals(1, rows.size());
        assertEquals("待确认上线", rows.get(0).path("statusName").asText());
        assertEquals("已上线待确认", rows.get(0).path("tasks").get(0).path("statusName").asText());
    }

    // ================================================================ 工具

    private int status() {
        return jdbc.queryForObject("select status from listing_task where id = ?", Integer.class, task);
    }

    private interface Step {
        Object call();
    }

    private int value(Step s) {
        AtomicInteger out = new AtomicInteger();
        TenantContext.runAs(A, () -> {
            Object v = s.call();
            out.set(v instanceof Integer i ? i : 0);
        });
        return out.get();
    }

    private void run(Runnable r) {
        TenantContext.runAs(A, r);
    }
}
