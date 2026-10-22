package com.zhul.erp.modules.listing;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhul.erp.framework.storage.FileStorageService;
import com.zhul.erp.modules.listing.service.ListingTaskBackfill;
import com.zhul.erp.support.TenantContractSupport;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * spec product/listing-task：按平台生成、取消与恢复、资料核对跨公司生成、列表页签与提醒、
 * 参考素材、回填发布结果、内容更新与素材替换、权限
 */
// 真实端口：打包下载要走完整的安全过滤与异步收尾（MockMvc 的 asyncDispatch 不经过这一步）
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ListingTaskContractTest extends TenantContractSupport {

    @LocalServerPort
    private int port;


    private static final int A = 99711;
    private static final int B = 99712;
    private static final String API = "/api/v1/listing-tasks";
    private static final String TASKS = "/api/v1/product/content-tasks";
    private static final String BRAND = "ITLBrand";
    private static final int MENU = 100117;

    private static final String ALIBABA_BODY = """
            ## 标题
            ITLBrand L-1 Servo Drive, Genuine New

            ## 关键词库
            | 关键词 | 档位 |
            |---|---|
            | L-1 servo | 核心 |
            | ITLBrand drive | 主要 |

            ## 卖点
            - Genuine
            - Ships in 24 hours
            """;

    @Autowired
    private ListingTaskBackfill backfill;
    @Autowired
    private FileStorageService files;

    private long brand;
    private long p1;
    private long p2;
    private String a;
    private String b;

    @BeforeEach
    void seed() {
        cleanup();
        jdbc.update("insert into product_brand (tenant_id, brand_name, status) values (0, ?, 1)", BRAND);
        brand = jdbc.queryForObject("select id from product_brand where brand_name = ?", Long.class, BRAND);
        p1 = product("L-1");
        p2 = product("L-2");
        loginAsAdmin("it_listing_admin");
        a = token("it_listing_admin", A);
        b = token("it_listing_admin", B);
    }

    @AfterEach
    void cleanup() {
        String products = "(select id from product where brand_id in (select id from product_brand where brand_name = '" + BRAND + "'))";
        for (String t : new String[] {"listing_task", "product_content_import", "product_content_task", "product_fact_task",
                "product_platform_content", "product_platform_keyword", "product_marketing_skip", "product_keyword",
                "product_keyword_use", "product_seo", "product_seo_faq", "product_locale"}) {
            jdbc.update("delete from " + t + " where product_id in " + products);
        }
        jdbc.update("delete from media_item where tenant_id in (?, ?)", A, B);
        jdbc.update("delete from product_media where file_key = 'it-listing/official.mp4'");
        jdbc.update("delete from biz_attachment where tenant_id in (?, ?)", A, B);
        jdbc.update("delete from product where brand_id in (select id from product_brand where brand_name = ?)", BRAND);
        jdbc.update("delete from product_brand where brand_name = ?", BRAND);
        jdbc.update("delete from sys_config where config_key = 'product.content.platforms' and tenant_id in (?, ?)", A, B);
        files.deleteQuietly("LOCAL", "it-listing/ref-1.png", false);
        files.deleteQuietly("LOCAL", "it-listing/official.mp4", true);
        files.deleteQuietly("LOCAL", "it-listing/ref-2.mp4", false);
    }

    @Test
    void generate_byPlatform_cancel_restore_acrossCompanies() throws Exception {
        // 资料还没核对：写完阿里国际站也不生成
        confirm(a, platformMd("alibaba", "L-1", "en", ALIBABA_BODY));
        assertEquals(0, tasks(A, p1).size());

        // 资料核对后生成阿里国际站
        ok(call(json(post(TASKS + "/products/" + p1 + "/verify-facts"), "{}"), a));
        assertEquals(Map.of("alibaba", 1), tasks(A, p1));

        // 独立站要英文、俄文都写完
        confirm(a, platformMd("site", "L-1", "en", "## SEO 标题\nL-1 Servo Drive\n"));
        assertFalse(tasks(A, p1).containsKey("site"));
        confirm(a, platformMd("site", "L-1", "ru", "## SEO 标题\nСервопривод L-1\n"));
        assertEquals(1, tasks(A, p1).get("site"));

        // 不会重复生成
        confirm(a, platformMd("alibaba", "L-1", "en", ALIBABA_BODY));
        assertEquals(2, tasks(A, p1).size());

        // 标不发布：待发布的取消，撤销后恢复
        ok(call(put(TASKS + "/products/" + p1 + "/skips/alibaba"), a));
        assertEquals(3, tasks(A, p1).get("alibaba"));
        assertEquals("标记不发布", reason(A, p1, "alibaba"));
        ok(call(delete(TASKS + "/products/" + p1 + "/skips/alibaba"), a));
        assertEquals(1, tasks(A, p1).get("alibaba"));
        assertEquals("", reason(A, p1, "alibaba"));

        // 标了不发布的平台写完也不生成
        ok(call(put(TASKS + "/products/" + p1 + "/skips/social"), a));
        confirm(a, platformMd("social", "L-1", "en", "## LinkedIn 帖子\nNew stock of L-1.\n"));
        assertFalse(tasks(A, p1).containsKey("social"));

        // 资料核对是共享的：两家公司都写完了 L-2 的阿里国际站，核对后两家都生成
        confirm(a, platformMd("alibaba", "L-2", "en", ALIBABA_BODY.replace("L-1", "L-2")));
        confirm(b, platformMd("alibaba", "L-2", "en", ALIBABA_BODY.replace("L-1", "L-2")));
        assertEquals(0, tasks(A, p2).size() + tasks(B, p2).size());
        ok(call(json(post(TASKS + "/products/" + p2 + "/verify-facts"), "{}"), a));
        assertEquals(Map.of("alibaba", 1), tasks(A, p2));
        assertEquals(Map.of("alibaba", 1), tasks(B, p2));

        // 停用平台：待发布的取消；已发布的不变
        long site = id(A, p1, "site");
        listingMedia("L-1", 1);
        ok(call(json(post(API + "/" + site + "/publish"), write(Map.of("listingUrl", "https://shop.example/l-1"))), a));
        ok(call(json(put(TASKS + "/platforms"), write(Map.of("platforms", List.of("alibaba")))), a));
        assertEquals(2, tasks(A, p1).get("site"), "已发布的不取消");
        ok(call(json(put(TASKS + "/platforms"), write(Map.of("platforms", List.of("mic")))), a));
        assertEquals(3, tasks(A, p1).get("alibaba"));
        assertEquals("平台已停用", reason(A, p1, "alibaba"));
        assertEquals(3, tasks(A, p2).get("alibaba"));
        assertEquals(1, tasks(B, p2).get("alibaba"), "公司 B 不受影响");
        ok(call(json(put(TASKS + "/platforms"), write(Map.of("platforms", List.of("alibaba", "site")))), a));
        assertEquals(1, tasks(A, p1).get("alibaba"));
    }

    @Test
    void list_detail_reference_publish_sync_replace_permissions() throws Exception {
        ok(call(json(post(TASKS + "/products/" + p1 + "/verify-facts"), "{}"), a));
        confirm(a, platformMd("alibaba", "L-1", "en", ALIBABA_BODY));
        long id = id(A, p1, "alibaba");

        // 列表按商品：一个商品一行，状态待发布并标素材待补
        JsonNode page = ok(call(json(post(API + "/page"), "{}"), a));
        assertEquals(1, page.path("total").asInt());
        JsonNode row = page.path("records").get(0);
        assertEquals("L-1", row.path("model").asText());
        assertEquals(BRAND, row.path("brand").asText());
        assertEquals("pending", row.path("status").asText());
        assertEquals("待发布", row.path("statusName").asText());
        assertTrue(row.path("needMedia").asBoolean());
        assertEquals(1, row.path("tasks").size());
        assertEquals("阿里国际站", row.path("tasks").get(0).path("platformLabel").asText());
        assertEquals(1, ok(call(json(post(API + "/page"), "{\"keyword\":\"l-1\"}"), a)).path("total").asInt());
        assertEquals(0, ok(call(json(post(API + "/page"), "{\"keyword\":\"l-9\"}"), a)).path("total").asInt());
        JsonNode counts = ok(call(json(post(API + "/counts"), "{}"), a));
        assertEquals(1, counts.path("pending").asInt());
        assertEquals(0, counts.path("published").asInt());

        // 再写完独立站：同一个商品仍是一行，两个平台
        confirm(a, platformMd("site", "L-1", "en", "## SEO 标题\nL-1 Servo Drive\n"));
        confirm(a, platformMd("site", "L-1", "ru", "## SEO 标题\nСервопривод L-1\n"));
        JsonNode two = ok(call(json(post(API + "/page"), "{\"tab\":\"all\"}"), a));
        assertEquals(1, two.path("total").asInt());
        assertEquals(List.of("alibaba", "site"), platforms(two.path("records").get(0).path("tasks")));
        JsonNode header = ok(call(get(API + "/products/" + p1), a));
        assertEquals(2, header.path("tasks").size());
        assertEquals("这个商品没有发品任务", fail(call(get(API + "/products/" + p2), a)).path("message").asText());

        // 详情：复制助手的营销内容
        JsonNode detail = ok(call(get(API + "/" + id), a));
        assertEquals("ITLBrand L-1 Servo Drive, Genuine New",
                detail.path("content").path("platformContents").path("alibaba").path("title").asText());
        assertEquals(0, detail.path("ownMedia").size());

        // 素材待补不能发布
        assertEquals("还没有发品素材，先补素材或选择参考素材",
                fail(call(json(post(API + "/" + id + "/publish"), write(Map.of("listingUrl", "https://a.example/x"))), a))
                        .path("message").asText());

        // 参考素材：只能选参考列表里的型号；选同品牌的 L-2
        long refItem = media("L-2", 9, 1, 0);
        JsonNode refs = ok(call(get(API + "/" + id + "/references"), a));
        assertTrue(refs.path("level").asInt() > 0, "同品牌的 L-2 有素材");

        // 选用前预览参考型号的素材：列表里的型号可以取，素材链接只限这个型号；不在列表里的拒绝
        JsonNode refMedia = ok(call(get(API + "/" + id + "/references/media").param("brand", BRAND).param("model", "L-2"), a));
        assertEquals(1, refMedia.size());
        assertEquals(refItem, refMedia.get(0).path("itemId").asLong());
        assertEquals(1, ok(call(json(post(API + "/" + id + "/references/urls").param("brand", BRAND).param("model", "L-2"),
                write(Map.of("refs", List.of(Map.of("itemId", refItem)), "download", false))), a)).size());
        assertEquals("只能从这个型号的参考素材里选择", fail(call(get(API + "/" + id + "/references/media")
                .param("brand", BRAND).param("model", "L-9"), a)).path("message").asText());
        assertEquals("L-2", refs.path("models").get(0).path("model").asText());
        assertEquals("只能从这个型号的参考素材里选择",
                fail(call(json(put(API + "/" + id + "/reference"), write(Map.of("brand", BRAND, "model", "L-9"))), a)).path("message").asText());
        JsonNode withRef = ok(call(json(put(API + "/" + id + "/reference"), write(Map.of("brand", BRAND, "model", "L-2"))), a));
        assertEquals("L-2", withRef.path("task").path("refModel").asText());
        assertFalse(withRef.path("task").path("needMedia").asBoolean());
        assertEquals(1, withRef.path("referenceMedia").size());
        assertEquals(1, ok(call(json(post(API + "/" + id + "/urls"), write(Map.of("refs", List.of(Map.of("itemId", refItem)),
                "download", true))), a)).size());

        // 回填：链接格式；成功后记录发布人
        assertEquals("平台链接要以 http:// 或 https:// 开头",
                fail(call(json(post(API + "/" + id + "/publish"), write(Map.of("listingUrl", "alibaba.com/xxx"))), a)).path("message").asText());
        JsonNode pub = ok(call(json(post(API + "/" + id + "/publish"), write(Map.of("listingUrl",
                "https://www.alibaba.com/product-detail/xxx.html", "platformItemId", "1600123456789", "note", "首发"))), a));
        assertEquals(2, pub.path("task").path("status").asInt());
        assertEquals("it_listing_admin", pub.path("task").path("publishedBy").asText());
        assertEquals("1600123456789", pub.path("task").path("platformItemId").asText());
        assertEquals("任务已发布", fail(call(json(post(API + "/" + id + "/publish"), write(Map.of("listingUrl", "https://a.example/x"))), a))
                .path("message").asText());
        JsonNode rec = ok(call(json(put(API + "/" + id + "/record"), write(Map.of("listingUrl", "https://www.alibaba.com/p/2.html"))), a));
        assertEquals("https://www.alibaba.com/p/2.html", rec.path("task").path("listingUrl").asText());
        assertEquals("it_listing_admin", rec.path("task").path("publishedBy").asText());

        // 内容有更新：发布后营销内容又写入过
        assertEquals("没有需要同步的更新", fail(call(post(API + "/" + id + "/sync"), a)).path("message").asText());
        jdbc.update("update listing_task set content_at = date_sub(content_at, interval 1 minute) where id = ?", id);
        JsonNode updatedCounts = ok(call(json(post(API + "/counts"), "{}"), a));
        assertEquals(1, updatedCounts.path("updated").asInt());
        assertEquals(0, updatedCounts.path("published").asInt(), "独立站还待发布，不算全部发完");
        JsonNode updatedRow = ok(call(json(post(API + "/page"), "{\"tab\":\"updated\"}"), a)).path("records").get(0);
        assertEquals("pending", updatedRow.path("status").asText(), "有待发布的平台时商品状态取待发布");
        assertTrue(updatedRow.path("tasks").get(0).path("contentUpdated").asBoolean());
        JsonNode synced = ok(call(post(API + "/" + id + "/sync"), a));
        assertFalse(synced.path("task").path("contentUpdated").asBoolean());
        assertEquals("it_listing_admin", synced.path("task").path("syncedBy").asText());

        // 待替换素材：本型号有了用于发品的素材
        assertEquals("没有需要替换的素材", fail(call(post(API + "/" + id + "/replace"), a)).path("message").asText());
        long own = listingMedia("L-1", 1);
        JsonNode replaceTab = ok(call(json(post(API + "/page"), "{\"tab\":\"replace\"}"), a));
        assertEquals(1, replaceTab.path("total").asInt());
        assertEquals(1, replaceTab.path("records").get(0).path("tasks").get(0).path("imageCount").asInt());
        JsonNode replaced = ok(call(post(API + "/" + id + "/replace"), a));
        assertTrue(replaced.path("task").path("refModel").isNull() || replaced.path("task").path("refModel").isMissingNode());
        assertEquals(1, replaced.path("ownMedia").size());
        assertEquals(0, replaced.path("referenceMedia").size());

        // 独立站也发布后：商品全部发完，进「已发布」，待发布页签里没有
        ok(call(json(post(API + "/" + id(A, p1, "site") + "/publish"), write(Map.of("listingUrl", "https://shop.example/l-1"))), a));
        JsonNode done = ok(call(json(post(API + "/counts"), "{}"), a));
        assertEquals(1, done.path("published").asInt());
        assertEquals(0, done.path("pending").asInt());
        assertEquals("published", ok(call(get(API + "/products/" + p1), a)).path("status").asText());

        // 链接只能取这个任务的素材
        assertEquals(1, ok(call(json(post(API + "/" + id + "/urls"), write(Map.of("refs", List.of(Map.of("itemId", own)),
                "download", false))), a)).size());
        assertEquals("素材不属于这个发品任务", fail(call(json(post(API + "/" + id + "/urls"), write(Map.of("refs",
                List.of(Map.of("itemId", refItem)), "download", false))), a)).path("message").asText());

        // 权限：只有菜单的看得到、不能处理；其他公司查不到
        loginWithResources("it_listing_viewer", MENU);
        String viewer = token("it_listing_viewer", A);
        assertEquals(1, ok(call(json(post(API + "/page"), "{\"tab\":\"all\"}"), viewer)).path("total").asInt());
        assertEquals(403, perform(json(post(API + "/" + id + "/publish"), write(Map.of("listingUrl", "https://a.example/x"))), viewer).getStatus());
        assertEquals(403, perform(post(API + "/" + id + "/sync"), viewer).getStatus());
        loginAsAdmin("it_listing_admin");
        assertEquals("发品任务不存在", fail(call(get(API + "/" + id), b)).path("message").asText());
        assertEquals(0, ok(call(json(post(API + "/page"), "{\"tab\":\"all\"}"), b)).path("total").asInt());
    }

    @Test
    void backfill_createsMissingTasks_once() {
        // 上线前就写完、资料已核对的商品：启动补齐后生成，再跑一次不重复
        jdbc.update("insert into product_fact_task (tenant_id, product_id, imported_at, verified_at) values (0, ?, now() - interval 1 day, now())", p1);
        jdbc.update("insert into product_content_task (tenant_id, product_id, status, alibaba_at, en_at) values (?, ?, 1, now(), now())", A, p1);
        backfill.run(null);
        assertEquals(Map.of("alibaba", 1), tasks(A, p1), "独立站只写了英文，不生成");
        backfill.run(null);
        assertEquals(1, tasks(A, p1).size());
    }

    @Test
    void referenceZip_packsOriginalNames_onlyListedModels() throws Exception {
        ok(call(json(post(TASKS + "/products/" + p1 + "/verify-facts"), "{}"), a));
        confirm(a, platformMd("alibaba", "L-1", "en", ALIBABA_BODY));
        long id = id(A, p1, "alibaba");
        // 参考型号 L-2 两个可发客户的文件，原文件名相同
        storedMedia("L-2", "it-listing/ref-1.png", "营销图.png", 1, "first-image".getBytes(StandardCharsets.UTF_8));
        storedMedia("L-2", "it-listing/ref-2.mp4", "营销图.png", 2, "second-video".getBytes(StandardCharsets.UTF_8));

        HttpResponse<byte[]> res = download(API + "/" + id + "/references/zip?brand=" + BRAND + "&model=L-2", a);
        assertEquals(200, res.statusCode());
        Matcher name = Pattern.compile("filename\\*=UTF-8''([^;]+)").matcher(res.headers().firstValue("Content-Disposition").orElse(""));
        assertTrue(name.find(), res.headers().toString());
        assertEquals("参考素材-ITLBrand-L-2.zip", URLDecoder.decode(name.group(1), StandardCharsets.UTF_8));
        Map<String, byte[]> entries = zipEntries(res);
        assertEquals(List.of("营销图.png", "营销图 (2).png"), new ArrayList<>(entries.keySet()), "原文件名，重名加序号");
        // 两个文件的内容都在包里（先后按素材库的显示顺序）
        List<String> contents = entries.values().stream().map(b -> new String(b, StandardCharsets.UTF_8)).sorted().toList();
        assertEquals(List.of("first-image", "second-video"), contents);

        // 不在参考列表的型号：按普通错误返回，不打包
        assertEquals("只能从这个型号的参考素材里选择", fail(call(get(API + "/" + id + "/references/zip")
                .param("brand", BRAND).param("model", "L-9"), a)).path("message").asText());

        // 选好参考素材后，详情里「参考素材」的全部下载也打成一个包，图片和视频都在
        assertEquals("还没有选参考素材，或参考型号没有可发客户的图片和视频", fail(call(get(API + "/" + id + "/media/zip")
                .param("source", "reference"), a)).path("message").asText());
        ok(call(json(put(API + "/" + id + "/reference"), write(Map.of("brand", BRAND, "model", "L-2"))), a));
        // 再加一个商品官方视频（在公开存储）和一个文件已丢失的素材
        Path tmp = Files.createTempFile("it-listing", ".mp4");
        Files.write(tmp, "official-video".getBytes(StandardCharsets.UTF_8));
        files.storages().local().put(true, "it-listing/official.mp4", tmp, "video/mp4");
        Files.deleteIfExists(tmp);
        jdbc.update("insert into product_media (tenant_id, product_id, media_type, file_url, storage_type, storage, file_key, file_size) "
                + "values (0, ?, 2, '/files/public/it-listing/official.mp4', 1, 'LOCAL', 'it-listing/official.mp4', 14)", p2);
        jdbc.update("insert into biz_attachment (tenant_id, owner_type, owner_id, kind, storage, file_key, url, file_name, file_size, content_type, uploaded_by) "
                + "values (?, 'SHOOT', 1, 1, 'LOCAL', 'it-listing/gone.png', '', '丢失.png', 1, 'image/png', 0)", A);
        long gone = jdbc.queryForObject("select max(id) from biz_attachment where tenant_id = ?", Long.class, A);
        jdbc.update("insert into media_item (tenant_id, asset_key, brand, model, category, kind, source_table, source_id, can_send, for_listing) "
                + "values (?, ?, ?, 'L-2', 9, 1, 'biz_attachment', ?, 1, 0)", A, (BRAND + " L-2").toLowerCase(), BRAND, gone);

        HttpResponse<byte[]> refRes = download(API + "/" + id + "/media/zip?source=reference", a);
        assertEquals(200, refRes.statusCode());
        Map<String, byte[]> refEntries = zipEntries(refRes);
        assertEquals(Set.of("营销图.png", "营销图 (2).png", "official.mp4", "说明.txt"), refEntries.keySet(),
                "官方视频从公开存储读取；丢失的文件跳过，压缩包仍能打开");
        assertEquals("official-video", new String(refEntries.get("official.mp4"), StandardCharsets.UTF_8));
        assertTrue(new String(refEntries.get("说明.txt"), StandardCharsets.UTF_8).contains("文件读取失败，没有打包：丢失.png"));
        // 本型号还没有发品素材
        assertEquals("这个型号还没有发品素材", fail(call(get(API + "/" + id + "/media/zip"), a)).path("message").asText());
    }

    /** 像解压软件一样从文件末尾的目录读：包被截断或后面追加了别的内容（如错误 JSON）时打不开 */
    /** 通过真实端口下载 */
    private HttpResponse<byte[]> download(String path, String token) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Authorization", "Bearer " + token).GET().build();
        return HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofByteArray());
    }

    private static Map<String, byte[]> zipEntries(HttpResponse<byte[]> res) throws Exception {
        Path tmp = Files.createTempFile("it-listing", ".zip");
        try {
            Files.write(tmp, res.body());
            Map<String, byte[]> entries = new LinkedHashMap<>();
            try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(tmp.toFile(), StandardCharsets.UTF_8)) {
                for (ZipEntry e : java.util.Collections.list(zip.entries())) {
                    try (InputStream in = zip.getInputStream(e)) {
                        entries.put(e.getName(), in.readAllBytes());
                    }
                }
            }
            byte[] body = res.body();
            // 结尾必须是 zip 的目录结束标记（22 字节，无注释）
            assertEquals(0x06054b50, java.nio.ByteBuffer.wrap(body, body.length - 22, 4)
                    .order(java.nio.ByteOrder.LITTLE_ENDIAN).getInt(), "压缩包后面不能有多余内容");
            return entries;
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    // ---------------------------------------------------------------- 准备数据

    /** 一条真实写入本地存储的可发客户素材 */
    private void storedMedia(String model, String key, String fileName, int kind, byte[] content) throws Exception {
        Path tmp = Files.createTempFile("it-listing", ".bin");
        Files.write(tmp, content);
        files.storages().local().put(false, key, tmp, kind == 1 ? "image/png" : "video/mp4");
        Files.deleteIfExists(tmp);
        jdbc.update("insert into biz_attachment (tenant_id, owner_type, owner_id, kind, storage, file_key, url, file_name, file_size, content_type, uploaded_by) "
                + "values (?, 'SHOOT', 1, ?, 'LOCAL', ?, '', ?, ?, 'image/png', 0)", A, kind, key, fileName, content.length);
        long att = jdbc.queryForObject("select max(id) from biz_attachment where tenant_id = ?", Long.class, A);
        jdbc.update("insert into media_item (tenant_id, asset_key, brand, model, category, kind, source_table, source_id, can_send, for_listing) "
                + "values (?, ?, ?, ?, 9, ?, 'biz_attachment', ?, 1, 0)", A, (BRAND + " " + model).toLowerCase(), BRAND, model, kind, att);
    }

    private long product(String mpn) {
        jdbc.update("insert into product (tenant_id, brand_id, mpn_raw, mpn_normalized, product_name) values (0, ?, ?, ?, ?)",
                brand, mpn, mpn.replace("-", "").toLowerCase(), BRAND + " " + mpn);
        return jdbc.queryForObject("select id from product where brand_id = ? and mpn_raw = ?", Long.class, brand, mpn);
    }

    private static String platformMd(String platform, String model, String lang, String body) {
        return "---\ntype: marketing\nplatform: " + platform + "\nbrand: " + BRAND + "\nmodel: " + model + "\nlang: " + lang
                + "\n---\n\n" + body;
    }

    private void confirm(String token, String content) throws Exception {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("fileName", "x.md");
        f.put("content", content);
        JsonNode res = ok(call(json(post(TASKS + "/marketing/confirm"), write(Map.of("files", List.of(f)))), token));
        assertEquals(1, res.path("succeeded").asInt(), res.toString());
    }

    /** 公司里这个商品的发品任务：平台 → 状态 */
    private Map<String, Integer> tasks(int tenantId, long productId) {
        Map<String, Integer> out = new LinkedHashMap<>();
        jdbc.queryForList("select platform, status from listing_task where tenant_id = ? and product_id = ? order by id", tenantId, productId)
                .forEach(r -> out.put((String) r.get("platform"), ((Number) r.get("status")).intValue()));
        return out;
    }

    private String reason(int tenantId, long productId, String platform) {
        return jdbc.queryForObject("select cancel_reason from listing_task where tenant_id = ? and product_id = ? and platform = ?",
                String.class, tenantId, productId, platform);
    }

    private long id(int tenantId, long productId, String platform) {
        return jdbc.queryForObject("select id from listing_task where tenant_id = ? and product_id = ? and platform = ?",
                Long.class, tenantId, productId, platform);
    }

    private static List<String> platforms(JsonNode tasks) {
        List<String> out = new ArrayList<>();
        tasks.forEach(t -> out.add(t.path("platform").asText()));
        return out;
    }

    private long listingMedia(String model, int kind) {
        long item = media(model, 9, kind, 1);
        jdbc.update("update media_item set for_listing = 1 where id = ?", item);
        return item;
    }

    /** 一条公司 A 的素材（可发客户），一个假附件 + 索引行 */
    private long media(String model, int category, int kind, int forListing) {
        jdbc.update("insert into biz_attachment (tenant_id, owner_type, owner_id, kind, storage, file_key, url, file_name, file_size, content_type, uploaded_by) "
                + "values (?, 'SHOOT', 1, ?, 'LOCAL', 'tenant/1/shoot/202610/0123456789abcdef0123456789abcdef.png', '', 'a.png', 1, 'image/png', 0)",
                A, kind);
        long att = jdbc.queryForObject("select max(id) from biz_attachment where tenant_id = ?", Long.class, A);
        jdbc.update("insert into media_item (tenant_id, asset_key, brand, model, category, kind, source_table, source_id, can_send, for_listing) "
                + "values (?, ?, ?, ?, ?, ?, 'biz_attachment', ?, 1, ?)", A, (BRAND + " " + model).toLowerCase(), BRAND, model, category, kind, att,
                forListing);
        return jdbc.queryForObject("select max(id) from media_item where tenant_id = ?", Long.class, A);
    }
}
