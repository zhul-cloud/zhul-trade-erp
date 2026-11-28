package com.zhul.erp.modules.website;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhul.erp.modules.website.service.WebsiteSiteService;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.support.TenantContractSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * spec product/website-site：新建与修改站点、凭据加密且不回显、留空不改、网址全平台唯一、停用、
 * 品类对照（二级 → 一级兜底）、租户隔离与权限；检测连接在没有配置发布服务时给出提示。
 */
class WebsiteSiteContractTest extends TenantContractSupport {

    private static final int A = 99731;
    private static final int B = 99732;
    private static final String API = "/api/v1/websites";
    private static final String URL = "https://it-site-publish.example.com";
    private static final String TOKEN = "ghp_itTestTokenValue0000a1b2";
    private static final String KEY = "-----BEGIN OPENSSH PRIVATE KEY-----\nb3BlbnNzaC1rZXktdjEAAAAABG5vbmUAAAAEbm9uZQ\nAAAAitTestKeyBodyQx9k\n-----END OPENSSH PRIVATE KEY-----";
    private static final String INDEXNOW = "itIndexNowKey00001234";

    @Autowired
    private WebsiteSiteService sites;

    private String a;
    private String b;

    @BeforeEach
    void seed() {
        cleanup();
        loginAsAdmin("it_site_admin");
        a = token("it_site_admin", A);
        b = token("it_site_admin", B);
    }

    @AfterEach
    void cleanup() {
        jdbc.update("delete from website_category_map where tenant_id in (?, ?)", A, B);
        jdbc.update("delete from website_site_secret where tenant_id in (?, ?)", A, B);
        jdbc.update("delete from website_site where tenant_id in (?, ?)", A, B);
        jdbc.update("delete from product_category where category_code like 'it_site_%'");
    }

    private Map<String, Object> site(String url) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", "Fouwell");
        m.put("url", url);
        m.put("languages", List.of(Map.of("code", "en", "prefix", ""), Map.of("code", "ru", "prefix", "/ru")));
        m.put("pathTemplate", "/products/{slug}/");
        m.put("publishMode", 1);
        m.put("repoUrl", "https://github.com/example/site.git");
        m.put("repoBranch", "main");
        m.put("deployHost", "gcam.example.com");
        m.put("deployPort", 18765);
        m.put("deployUser", "u1796");
        m.put("deployPath", "www/example.com/public_html");
        m.put("needPurge", true);
        m.put("purgeNote", "SiteGround → Speed → Caching → Flush");
        m.put("repoToken", TOKEN);
        m.put("deployKey", KEY);
        m.put("indexNowKey", INDEXNOW);
        return m;
    }

    private long create(String token, Map<String, Object> body) throws Exception {
        return ok(call(json(post(API), write(body)), token)).path("id").asLong();
    }

    // ================================================================ 新建与凭据

    @Test
    void create_secretsEncrypted_onlyLast4Returned_neverPlaintext() throws Exception {
        String raw = perform(json(post(API), write(site(URL))), a).getContentAsString(StandardCharsets.UTF_8);
        JsonNode d = ok(objectMapper.readTree(raw));
        assertFalse(raw.contains(TOKEN) || raw.contains("itTestKeyBody") || raw.contains(INDEXNOW), "新建的返回里没有凭据明文");
        assertEquals("接管中", d.path("takeoverStatusName").asText());
        assertEquals("a1b2", d.path("repoToken").path("last4").asText());
        assertTrue(d.path("deployKey").path("configured").asBoolean());
        assertEquals("Qx9k", d.path("deployKey").path("last4").asText());
        assertEquals("1.05", d.path("priceFactor").asText());
        long id = d.path("id").asLong();

        String detail = perform(get(API + "/" + id), a).getContentAsString(StandardCharsets.UTF_8);
        String list = perform(get(API), a).getContentAsString(StandardCharsets.UTF_8);
        for (String body : List.of(detail, list)) {
            assertFalse(body.contains(TOKEN) || body.contains("itTestKeyBody") || body.contains(INDEXNOW), "详情和列表里没有凭据明文");
        }
        for (String cipher : jdbc.queryForList("select cipher_text from website_site_secret where site_id = ?", String.class, id)) {
            assertTrue(cipher.startsWith("v2:"), "AES-256-GCM 密文");
            assertFalse(cipher.contains(TOKEN) || cipher.contains("itTestKeyBody") || cipher.contains(INDEXNOW));
        }
        // 内部提交给发布服务时才解密
        AtomicReference<Map<String, String>> plain = new AtomicReference<>();
        TenantContext.runAs(A, () -> plain.set(sites.publisherSecrets(id)));
        assertEquals(TOKEN, plain.get().get("repoToken"));
        assertEquals(KEY + "\n", plain.get().get("deployKey"));
    }

    @Test
    void update_blankSecretsKeepOld_newOneReplaces_indexNowCanBeCleared() throws Exception {
        long id = create(a, site(URL));
        Map<String, Object> body = site(URL);
        body.put("repoToken", "");
        body.put("deployKey", null);
        body.remove("indexNowKey");
        body.put("priceFactor", "1.2");
        JsonNode d = ok(call(json(put(API + "/" + id), write(body)), a));
        assertEquals("a1b2", d.path("repoToken").path("last4").asText(), "留空表示不改");
        assertEquals("Qx9k", d.path("deployKey").path("last4").asText());
        assertEquals(0, new BigDecimal("1.2").compareTo(d.path("priceFactor").decimalValue()));

        body.put("repoToken", "ghp_newTokenzz99");
        body.put("clearIndexNowKey", true);
        d = ok(call(json(put(API + "/" + id), write(body)), a));
        assertEquals("zz99", d.path("repoToken").path("last4").asText());
        assertFalse(d.path("indexNowKey").path("configured").asBoolean());
    }

    @Test
    void validation_messages() throws Exception {
        Map<String, Object> body = site("https://it-site-publish.example.com/en");
        assertEquals("网址要以 https:// 开头，不带路径，如 https://fouwell.com", fail(call(json(post(API), write(body)), a)).path("message").asText());

        body = site(URL);
        body.remove("deployKey");
        assertEquals("自动发布需要填写部署私钥", fail(call(json(post(API), write(body)), a)).path("message").asText());

        body = site(URL);
        body.put("deployKey", "-----BEGIN OPENSSH PRIVATE KEY-----\nProc-Type: 4,ENCRYPTED\nxxxx\n-----END OPENSSH PRIVATE KEY-----");
        assertEquals("部署私钥带密码，发布服务无法使用，请换一把不带密码的私钥",
                fail(call(json(post(API), write(body)), a)).path("message").asText());

        body = site(URL);
        body.put("pathTemplate", "/products/");
        fail(call(json(post(API), write(body)), a));

        body = site(URL);
        body.put("priceFactor", "12");
        assertEquals("售价系数在 0.5 到 10 之间，最多两位小数", fail(call(json(post(API), write(body)), a)).path("message").asText());

        body = site(URL);
        body.put("repoUrl", "https://user:pass@github.com/example/site.git");
        fail(call(json(post(API), write(body)), a));

        body = site(URL);
        body.put("needPurge", true);
        body.put("purgeNote", "");
        assertEquals("需要人工清缓存时，请填写清缓存的操作说明", fail(call(json(post(API), write(body)), a)).path("message").asText());

        // 只复制：不需要仓库与部署
        Map<String, Object> copy = new HashMap<>(Map.of("name", "Copy", "url", URL, "publishMode", 2, "pathTemplate", "/p/{slug}/",
                "languages", List.of(Map.of("code", "en", "prefix", ""))));
        JsonNode d = ok(call(json(post(API), write(copy)), a));
        assertEquals("只复制", d.path("publishModeName").asText());
        assertEquals("「只复制」的站点不需要检测连接",
                fail(call(post(API + "/" + d.path("id").asLong() + "/check"), a)).path("message").asText());
    }

    @Test
    void url_uniqueAcrossTenants_nameUniqueWithinTenant() throws Exception {
        create(a, site(URL));
        assertEquals("这个网址已被其他站点使用", fail(call(json(post(API), write(site(URL + "/"))), b)).path("message").asText());
        Map<String, Object> same = site("https://it-site-publish-2.example.com");
        assertEquals("本公司已有同名站点", fail(call(json(post(API), write(same)), a)).path("message").asText());
        create(b, same);
    }

    // ================================================================ 停用、检测连接

    @Test
    void disable_andCheckWithoutPublisher() throws Exception {
        long id = create(a, site(URL));
        assertEquals(0, ok(call(json(put(API + "/" + id + "/status"), "{\"status\":0}"), a)).path("status").asInt());
        assertEquals("还没有配置独立站发布服务，请联系管理员", fail(call(post(API + "/" + id + "/check"), a)).path("message").asText());
    }

    // ================================================================ 品类对照

    @Test
    void categoryMap_secondLevelFallsBackToFirst() throws Exception {
        long id = create(a, site(URL));
        jdbc.update("insert into product_category (tenant_id, category_code, category_name, sort_order) values (0, 'it_site_sensors', 'IT Sensors', 9990)");
        long sensors = jdbc.queryForObject("select id from product_category where category_code = 'it_site_sensors'", Long.class);
        jdbc.update("insert into product_category (tenant_id, category_code, category_name, parent_id, sort_order) values (0, 'it_site_prox', 'IT Proximity', ?, 1)", sensors);
        jdbc.update("insert into product_category (tenant_id, category_code, category_name, parent_id, sort_order) values (0, 'it_site_photo', 'IT Photo', ?, 2)", sensors);
        long prox = jdbc.queryForObject("select id from product_category where category_code = 'it_site_prox'", Long.class);
        long photo = jdbc.queryForObject("select id from product_category where category_code = 'it_site_photo'", Long.class);

        String req = write(Map.of("items", List.of(Map.of("categoryId", sensors, "siteCategory", "Sensors"),
                Map.of("categoryId", photo, "siteCategory", "photo-sensors"), Map.of("categoryId", prox, "siteCategory", ""))));
        JsonNode tree = ok(call(json(put(API + "/" + id + "/category-map"), req), a));
        JsonNode row = null;
        for (JsonNode n : tree) {
            if (n.path("categoryId").asLong() == sensors) {
                row = n;
            }
        }
        assertEquals("sensors", row.path("siteCategory").asText(), "转成小写");
        assertEquals(2, row.path("children").size());

        AtomicReference<String> p = new AtomicReference<>();
        AtomicReference<String> f = new AtomicReference<>();
        TenantContext.runAs(A, () -> {
            p.set(sites.siteCategory(id, prox));
            f.set(sites.siteCategory(id, photo));
        });
        assertEquals("sensors", p.get(), "二级没设置用一级");
        assertEquals("photo-sensors", f.get());

        assertEquals("站点品类「bad code」不正确：只能用小写字母、数字、- 和 _",
                fail(call(json(put(API + "/" + id + "/category-map"),
                        write(Map.of("items", List.of(Map.of("categoryId", sensors, "siteCategory", "bad code"))))), a)).path("message").asText());
    }

    // ================================================================ 隔离与权限

    @Test
    void tenantIsolation_andViewOnlyPermission() throws Exception {
        long id = create(a, site(URL));
        fail(call(get(API + "/" + id), b));
        assertEquals(0, ok(call(get(API), b)).size());
        fail(call(json(put(API + "/" + id), write(site(URL))), b));

        loginWithResources("it_site_view", 100101, 100134);
        String view = token("it_site_view", A);
        JsonNode d = ok(call(get(API + "/" + id), view));
        assertTrue(d.path("repoToken").path("configured").asBoolean(), "只能看：凭据只显示是否已配置");
        fail(call(json(put(API + "/" + id), write(site(URL))), view));
        fail(call(post(API + "/" + id + "/check"), view));
        fail(call(json(post(API), write(site("https://it-site-publish-3.example.com"))), view));
    }
}
