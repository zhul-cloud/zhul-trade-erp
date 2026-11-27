package com.zhul.erp.modules.video.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhul.erp.framework.storage.FileStorageService;
import com.zhul.erp.modules.aitask.constants.AiTaskStatus;
import com.zhul.erp.modules.aitask.entity.AiTaskDO;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.framework.tenant.TenantScope;
import com.zhul.erp.modules.attachment.repository.BizAttachmentMapper;
import com.zhul.erp.modules.video.constants.VideoConstants;
import com.zhul.erp.modules.video.repository.VideoTaskMapper;
import com.zhul.erp.modules.video.support.VideoSettings;
import com.zhul.erp.modules.video.support.VideoWindow;
import com.zhul.erp.support.TenantContractSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * spec product/video-task：上传与批量匹配、状态流转（合法与非法）、加入队列的校验、夜间派发、生成回调（另一个线程，没有租户上下文）、
 * 超时、审核与素材库替换、租户隔离、权限；spec product/product「本公司素材」与设为官方素材
 */
class VideoTaskContractTest extends TenantContractSupport {

    private static final int A = 99721;
    private static final int B = 99722;
    private static final String API = "/api/v1/video-tasks";
    private static final String GEN = "/api/v1/video-generator/tasks/";
    private static final String BRAND = "ITVBrand";
    private static final String OTHER = "ITVOther";
    private static final String TOKEN = "zhul-erp-video-callback-secret";
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 'I', 'H', 'D', 'R'};
    private static final byte[] HEIC = {0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'h', 'e', 'i', 'c', 0, 0, 0, 0};
    private static final byte[] MP4 = {0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm', 0, 0, 2, 0};
    private static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F', 0};

    @Autowired
    private VideoTaskMapper taskMapper;
    @Autowired
    private BizAttachmentMapper attachmentMapper;
    @Autowired
    private VideoTaskService tasks;
    @Autowired
    private VideoResultService results;
    @Autowired
    private VideoSettings settings;
    @Autowired
    private FileStorageService files;
    @Autowired
    private VideoScriptResultHandler scriptHandler;

    private long p1;
    private long p2;
    private long p2other;
    private String a;
    private String b;

    @BeforeEach
    void seed() {
        cleanup();
        long brand = brand(BRAND);
        long other = brand(OTHER);
        p1 = product(brand, "V-1");
        p2 = product(brand, "V-2");
        p2other = product(other, "V-2");
        loginAsAdmin("it_video_admin");
        a = token("it_video_admin", A);
        b = token("it_video_admin", B);
    }

    @AfterEach
    void cleanup() {
        for (Map<String, Object> r : jdbc.queryForList("select storage, file_key from biz_attachment where tenant_id in (?, ?, 0) "
                + "and owner_type in ('VIDEO_TASK', 'VIDEO_OUTPUT')", A, B)) {
            files.deleteQuietly((String) r.get("storage"), (String) r.get("file_key"), false);
        }
        String products = "(select id from product where brand_id in (select id from product_brand where brand_name in ('" + BRAND + "', '" + OTHER + "')))";
        for (Map<String, Object> r : jdbc.queryForList("select storage, file_key, cover_url from product_media where product_id in " + products)) {
            files.deleteQuietly((String) r.get("storage"), (String) r.get("file_key"), true);
        }
        jdbc.update("delete from product_media where product_id in " + products);
        jdbc.update("delete from product_video_log where task_id in (select id from product_video_task where product_id in " + products + ")");
        jdbc.update("delete from product_video_output where task_id in (select id from product_video_task where product_id in " + products + ")");
        jdbc.update("delete from product_video_material where task_id in (select id from product_video_task where product_id in " + products + ")");
        jdbc.update("delete from product_video_task where product_id in " + products);
        jdbc.update("delete from media_item where asset_key like 'itvbrand %'");
        jdbc.update("delete from biz_attachment where owner_type in ('VIDEO_TASK', 'VIDEO_OUTPUT') and tenant_id in (?, ?, 0)", A, B);
        jdbc.update("delete from product where brand_id in (select id from product_brand where brand_name in (?, ?))", BRAND, OTHER);
        jdbc.update("delete from product_brand where brand_name in (?, ?)", BRAND, OTHER);
        redis.delete(VideoConstants.PAUSE_KEY);
        redis.delete("zhul:erp:video:unavailable");
    }

    // ================================================================ 上传与批量匹配

    @Test
    void upload_createsOneTaskPerProduct_rejectsHeic_andEntersMediaLibrary() throws Exception {
        JsonNode heic = fail(call(upload(p1, "IMG_0001.heic", HEIC), a));
        assertEquals("只支持 JPG、PNG、WEBP 图片，请先转成 JPG", heic.path("message").asText());
        // 扩展名对但文件头是 HEIC 也拒绝
        fail(call(upload(p1, "fake.jpg", HEIC), a));

        long id = uploadImages(a, p1, 3);
        assertEquals(id, uploadImages(a, p1, 1), "同一商品追加到原来的任务");
        assertEquals(1, jdbc.queryForObject("select count(*) from product_video_task where product_id = ?", Integer.class, p1));
        JsonNode d = ok(call(get(API + "/" + id), a));
        assertEquals("待生成", d.path("statusName").asText());
        assertEquals(4, d.path("materials").size());
        assertEquals(4, imageAssets(A), "上传的图片立即作为图片素材进入素材库");
        assertEquals(4, jdbc.queryForObject("select count(*) from media_item where tenant_id = ? and category = 12 and for_listing = 1 "
                + "and can_send = 1 and deleted_at is null", Integer.class, A), "默认可发客户、用于发品");

        // 删除一张：同时移出素材库
        long first = d.path("materials").get(0).path("attachmentId").asLong();
        ok(call(delete(API + "/" + id + "/materials/" + first), a));
        assertEquals(3, imageAssets(A));

        // 最多 12 张
        uploadImages(a, p1, 9);
        assertEquals("一个任务最多 12 张图片", fail(call(upload(p1, "13.png", PNG), a)).path("message").asText());
    }

    @Test
    void folderMatch_matched_multiple_none() throws Exception {
        uploadImages(a, p1, 1);
        JsonNode r = ok(call(json(post(API + "/folders/match"),
                "{\"folders\":[\" v-1 \",\"" + BRAND + "_V-2\",\"V-2\",\"ABC-123\"]}"), a));
        assertEquals("matched", r.get(0).path("result").asText());
        assertEquals(p1, r.get(0).path("products").get(0).path("id").asLong());
        assertTrue(r.get(0).path("hasTask").asBoolean(), "已有任务的追加");
        assertEquals("matched", r.get(1).path("result").asText(), "品牌_型号按品牌加型号匹配");
        assertEquals(p2, r.get(1).path("products").get(0).path("id").asLong());
        assertFalse(r.get(1).path("hasTask").asBoolean());
        assertEquals("multiple", r.get(2).path("result").asText());
        assertEquals(List.of(p2, p2other), ids(r.get(2).path("products")));
        assertEquals("none", r.get(3).path("result").asText());

        // 别家公司询到的候选商品：本公司选不到，也不能匹配（否则上传时报商品不存在）
        jdbc.update("update product set archive_status = 1 where id = ?", p2);
        JsonNode c = ok(call(json(post(API + "/folders/match"), "{\"folders\":[\"" + BRAND + "_V-2\"]}"), a));
        assertEquals("none", c.get(0).path("result").asText());
    }

    // ================================================================ 状态流转与审核

    @Test
    void lifecycle_queue_generate_review_replace() throws Exception {
        long id = uploadImages(a, p1, 5);
        List<Long> imgs = materialIds(id);

        // 选用不够
        ok(call(json(put(API + "/" + id + "/materials"), materialsBody(imgs, 3)), a));
        assertEquals("选用 3 张，至少要 4 张", fail(call(post(API + "/" + id + "/queue"), a)).path("message").asText());
        ok(call(json(put(API + "/" + id + "/materials"), materialsBody(imgs, 5)), a));
        assertEquals("还没有脚本", fail(call(post(API + "/" + id + "/queue"), a)).path("message").asText());

        // 字幕太长：60 个词估算 24 秒
        JsonNode saved = ok(call(json(put(API + "/" + id + "/script"), scriptBody(imgs.subList(0, 4), 15)), a));
        assertTrue(saved.path("scriptError").asText().contains("估算时长 24 秒"));
        assertEquals("估算时长 24 秒，需要 15–20 秒，请精简字幕", fail(call(post(API + "/" + id + "/queue"), a)).path("message").asText());
        ok(call(json(put(API + "/" + id + "/script"), scriptBody(imgs.subList(0, 4), 10)), a));

        // 非法流转：待生成不能审核、不能移出队列
        assertTrue(fail(call(post(API + "/" + id + "/approve"), a)).path("message").asText().contains("「待生成」，不能通过"));
        assertTrue(fail(call(post(API + "/" + id + "/dequeue"), a)).path("message").asText().contains("不能移出队列"));

        // 加入队列 → 排队中不能改素材 → 移出 → 再加入
        JsonNode q = ok(call(post(API + "/" + id + "/queue"), a));
        assertEquals("排队中", q.path("statusName").asText());
        assertEquals(1, q.path("queuePosition").asInt());
        assertTrue(fail(call(upload(p1, "x.png", PNG), a)).path("message").asText().contains("不能修改素材"));
        assertEquals("待生成", ok(call(post(API + "/" + id + "/dequeue"), a)).path("statusName").asText());
        ok(call(post(API + "/" + id + "/queue"), a));
        assertEquals("任务当前为「排队中」，不能加入队列", fail(call(post(API + "/" + id + "/queue"), a)).path("message").asText());

        // 开始生成：生成中不能退回
        claim(id);
        assertEquals("任务正在生成，生成完成后再审核",
                fail(call(json(post(API + "/" + id + "/reject"), "{\"reason\":\"x\"}"), a)).path("message").asText());

        // 令牌不对的回调拒绝
        assertEquals(401, perform(resultRequest(id, 1, "wrong"), "none").getStatus());
        // 生成服务回调：在另一个线程里、没有租户上下文
        callbackInThread(resultRequest(id, 1, TOKEN));
        JsonNode r = ok(call(get(API + "/" + id), a));
        assertEquals("待审核", r.path("statusName").asText());
        assertEquals(2, r.path("outputs").size());
        assertEquals(0, videoAssets(A), "待审核的视频不进素材库");
        assertEquals(VideoConstants.SYSTEM, r.path("logs").get(0).path("operator").asText(),
                "生成服务回调没有登录用户，历史里记为系统");
        assertEquals(List.of("V-1_16x9_v1.jpg", "V-1_16x9_v1.mp4", "V-1_9x16_v1.jpg", "V-1_9x16_v1.mp4"),
                jdbc.queryForList("select file_name from biz_attachment where owner_type = ? and owner_id in"
                        + " (select id from product_video_output where task_id = ?) order by file_name",
                        String.class, VideoConstants.OWNER_OUTPUT, id), "成片按型号、画幅、版本命名");

        // 退回要写原因
        assertEquals("请填写退回原因", fail(call(json(post(API + "/" + id + "/reject"), "{\"reason\":\" \"}"), a)).path("message").asText());
        ok(call(post(API + "/" + id + "/approve"), a));
        assertEquals(2, videoAssets(A), "通过后两个视频进素材库，默认用于发品");
        List<Long> v1 = videoAssetSources(A);

        // 重新生成：通过前素材库仍是旧视频
        ok(call(post(API + "/" + id + "/queue"), a));
        claim(id);
        callbackInThread(resultRequest(id, 2, TOKEN));
        assertEquals(v1, videoAssetSources(A), "新视频审核通过前仍是原来的视频");
        JsonNode review = ok(call(get(API + "/" + id), a));
        assertEquals(2, review.path("approvedOutputs").size());
        assertEquals(2, review.path("outputs").path(0).path("version").asInt());

        // 迟到的旧版本回调丢弃
        callbackInThread(resultRequest(id, 1, TOKEN));
        assertEquals(2, jdbc.queryForObject("select count(*) from product_video_output where task_id = ? and version = 1", Integer.class, id));

        ok(call(post(API + "/" + id + "/approve"), a));
        List<Long> v2 = videoAssetSources(A);
        assertEquals(2, v2.size());
        assertTrue(v2.stream().noneMatch(v1::contains), "通过后旧视频移出素材库、换成新视频");
        assertEquals(2, jdbc.queryForObject("select count(*) from product_video_output where task_id = ? and status = 3", Integer.class, id));
        assertEquals("已通过", ok(call(get(API + "/" + id), a)).path("statusName").asText());
    }

    @Test
    void generatorFailure_andTimeout() throws Exception {
        long id = readyTask(a, p1);
        ok(call(post(API + "/" + id + "/queue"), a));
        claim(id);
        MockMultipartHttpServletRequestBuilder failed = multipart(GEN + id + "/result");
        failed.param("version", "1").param("status", "failed").param("error", "竖屏：配音服务不可用");
        failed.header(VideoGeneratorClient.TOKEN_HEADER, TOKEN);
        callbackInThread(failed);
        JsonNode d = ok(call(get(API + "/" + id), a));
        assertEquals("生成失败", d.path("statusName").asText());
        assertEquals("竖屏：配音服务不可用", d.path("failReason").asText());

        // 失败可以重新加入；生成中超过 15 分钟算超时
        ok(call(post(API + "/" + id + "/queue"), a));
        claim(id);
        jdbc.update("update product_video_task set started_at = ? where id = ?", LocalDateTime.now().minusMinutes(31), id);
        VideoQueueJob job = job(VideoGeneratorClient.Outcome.ACCEPTED, new ArrayList<>());
        TenantScope.platform(() -> job.expire(LocalDateTime.now()));
        assertEquals("生成超时", ok(call(get(API + "/" + id), a)).path("failReason").asText());
    }

    // ================================================================ AI 脚本

    @Test
    void aiScript_mapsImageIndexes_validates_andFailureAllowsManual() throws Exception {
        long id = uploadImages(a, p1, 5);
        List<Long> imgs = materialIds(id);
        jdbc.update("update product_video_task set script_status = 1, script_ai_task_id = 987654321 where id = ?", id);
        String words = String.join(" ", java.util.Collections.nCopies(10, "word"));
        AiTaskDO ai = new AiTaskDO();
        ai.setId(987654321L);
        ai.setSkillId(VideoConstants.AI_SKILL);
        ai.setStatus(AiTaskStatus.COMPLETED);
        ai.setOutput("{\"title\":\"ITVBrand V-1\",\"shots\":[{\"image\":2,\"caption\":\"" + words + "\"},{\"image\":1,\"caption\":\""
                + words + "\"},{\"image\":3,\"caption\":\"" + words + "\"},{\"image\":9,\"caption\":\"x\"},{\"image\":4,\"caption\":\""
                + words + "\"}]}");
        TenantContext.runAs(A, () -> scriptHandler.handle(ai));
        JsonNode d = ok(call(get(API + "/" + id), a));
        assertEquals(VideoConstants.SCRIPT_READY, d.path("scriptStatus").asInt());
        assertEquals(VideoConstants.SCRIPT_SOURCE_AI, d.path("scriptSource").asInt());
        assertEquals("", d.path("scriptError").asText());
        assertEquals(4, d.path("script").path("shots").size(), "序号超出选用图片的镜头丢弃");
        assertEquals(imgs.get(1), d.path("script").path("shots").get(0).path("attachmentId").asLong());
        assertEquals(16.0, d.path("totalSeconds").asDouble());
        assertTrue(d.path("factsUnverified").asBoolean(), "没有资料任务时提示资料未核对");

        // 过期的结果丢弃；失败时提示原因，可以手写
        TenantContext.runAs(A, () -> scriptHandler.handle(ai));
        jdbc.update("update product_video_task set script_status = 1 where id = ?", id);
        ai.setStatus(AiTaskStatus.FAILED);
        ai.setErrorMessage("AI 未配置");
        TenantContext.runAs(A, () -> scriptHandler.handle(ai));
        d = ok(call(get(API + "/" + id), a));
        assertEquals(VideoConstants.SCRIPT_FAILED, d.path("scriptStatus").asInt());
        assertTrue(d.path("scriptError").asText().contains("AI 未配置"));
        assertEquals(4, d.path("script").path("shots").size(), "失败不清掉已有脚本");
    }

    // ================================================================ 队列派发

    @Test
    void dispatch_oneAtATime_inQueueOrder_retryAndPause() throws Exception {
        long first = readyTask(a, p1);
        long second = readyTask(b, p2);
        ok(call(post(API + "/" + second + "/queue"), b));
        Thread.sleep(1100);
        ok(call(post(API + "/" + first + "/queue"), a));
        VideoWindow allDay = VideoWindow.parse("00:00-00:00");

        // 时段外不派发
        List<VideoGeneratorClient.Request> sent = new ArrayList<>();
        VideoQueueJob ok = job(VideoGeneratorClient.Outcome.ACCEPTED, sent);
        TenantScope.platform(() -> ok.dispatch(VideoWindow.parse("20:00-07:00"), LocalDateTime.now().withHour(12)));
        assertTrue(sent.isEmpty());

        // 按加入队列先后：B 的任务先加入，先生成；同一时间只生成一个
        TenantScope.platform(() -> ok.dispatch(allDay, LocalDateTime.now()));
        TenantScope.platform(() -> ok.dispatch(allDay, LocalDateTime.now()));
        assertEquals(1, sent.size());
        assertEquals(second, sent.get(0).taskId());
        assertEquals(4, sent.get(0).shots().size());
        assertTrue(sent.get(0).images().get(0).url().contains("/api/v1/video-generator/tasks/" + second + "/images/"));
        assertEquals(VideoConstants.STATUS_GENERATING, status(second));
        assertEquals(1, ok(call(get(API + "/" + first), a)).path("queuePosition").asInt(), "只算本公司能看到的位置");

        // 生成服务取图：令牌对、任务在生成中才能取
        long img = materialIds(second).get(0);
        assertEquals(401, perform(get(GEN + second + "/images/" + img), "none").getStatus());
        assertEquals(200, mvc.perform(get(GEN + second + "/images/" + img).header(VideoGeneratorClient.TOKEN_HEADER, TOKEN))
                .andReturn().getResponse().getStatus());

        // 服务不可用：退回排队、10 分钟后重试；连续 3 次暂停当晚
        jdbc.update("update product_video_task set status = 7 where id = ?", second);
        VideoQueueJob down = job(VideoGeneratorClient.Outcome.UNAVAILABLE, new ArrayList<>());
        for (int i = 0; i < 3; i++) {
            jdbc.update("update product_video_task set next_try_at = null where id = ?", first);
            TenantScope.platform(() -> down.dispatch(allDay, LocalDateTime.now()));
            assertEquals(VideoConstants.STATUS_QUEUED, status(first));
        }
        assertTrue(jdbc.queryForObject("select next_try_at from product_video_task where id = ?", LocalDateTime.class, first)
                .isAfter(LocalDateTime.now().plusMinutes(9)));
        assertEquals(0, jdbc.queryForObject("select current_version from product_video_task where id = ?", Integer.class, first));
        assertTrue(Boolean.TRUE.equals(redis.hasKey(VideoConstants.PAUSE_KEY)), "连续 3 次不可用暂停当晚");
        jdbc.update("update product_video_task set next_try_at = null where id = ?", first);
        TenantScope.platform(() -> ok.dispatch(allDay, LocalDateTime.now()));
        assertEquals(VideoConstants.STATUS_QUEUED, status(first), "暂停期间不派发");
    }

    // ================================================================ 隔离与权限

    @Test
    void tenantIsolation_andPermissions() throws Exception {
        long id = uploadImages(a, p1, 1);
        fail(call(get(API + "/" + id), b));
        assertEquals(0, ok(call(json(post(API + "/page"), "{\"tab\":\"all\"}"), b)).path("total").asInt());
        assertEquals(1, ok(call(json(post(API + "/page"), "{\"tab\":\"all\"}"), a)).path("total").asInt());
        assertEquals(1, ok(call(json(post(API + "/counts"), "{}"), a)).path("pending").asInt());
        fail(call(json(put(API + "/settings/window"), "{\"window\":\"21:00-06:00\"}"), a));
        assertEquals("20:00-07:00", ok(call(get(API + "/settings/window"), a)).path("window").asText());

        // 只有上传权限：能看、能传，不能通过
        loginWithResources("it_video_up", 100101, 100133, 110256);
        String up = token("it_video_up", A);
        ok(call(get(API + "/" + id), up));
        fail(call(post(API + "/" + id + "/approve"), up));
        assertEquals(VideoConstants.STATUS_PENDING, status(id));
    }

    @Test
    void companyMedia_isolated_andMakeOfficialOnce() throws Exception {
        uploadImages(a, p1, 2);
        assertEquals(2, ok(call(get("/api/v1/products/" + p1 + "/company-media"), a)).path("items").size());
        assertEquals(0, ok(call(get("/api/v1/products/" + p1 + "/company-media"), b)).path("items").size(), "其他公司看不到");
        long item = ok(call(get("/api/v1/products/" + p1 + "/company-media"), a)).path("items").get(0).path("media").path("itemId").asLong();
        assertEquals("只有平台账号能设为官方素材",
                fail(call(post("/api/v1/products/" + p1 + "/company-media/" + item + "/official"), a)).path("message").asText());

        // 平台账号自己的素材设为官方：复制到共享商品库，不能重复设置
        String platform = token("it_video_admin", 0);
        uploadImages(platform, p1, 1);
        long own = ok(call(get("/api/v1/products/" + p1 + "/company-media"), platform)).path("items").get(0).path("media").path("itemId").asLong();
        ok(call(post("/api/v1/products/" + p1 + "/company-media/" + own + "/official"), platform));
        assertEquals("公司素材", jdbc.queryForObject("select source from product_media where product_id = ? and deleted_at is null",
                String.class, p1));
        assertTrue(ok(call(get("/api/v1/products/" + p1 + "/company-media"), platform)).path("items").get(0).path("official").asBoolean());
        assertEquals("已是官方素材",
                fail(call(post("/api/v1/products/" + p1 + "/company-media/" + own + "/official"), platform)).path("message").asText());
    }

    // ================================================================ 工具

    private long brand(String name) {
        jdbc.update("insert into product_brand (tenant_id, brand_name, status) values (0, ?, 1)", name);
        return jdbc.queryForObject("select id from product_brand where brand_name = ?", Long.class, name);
    }

    private long product(long brand, String mpn) {
        jdbc.update("insert into product (tenant_id, brand_id, brand_key, mpn_raw, mpn_normalized, mpn_display, product_name) "
                + "values (0, ?, ?, ?, ?, ?, ?)", brand, "itv-" + brand, mpn, mpn.replace("-", "").toLowerCase(), mpn, "IT " + mpn);
        return jdbc.queryForObject("select id from product where brand_id = ? and mpn_raw = ?", Long.class, brand, mpn);
    }

    private static MockMultipartHttpServletRequestBuilder upload(long productId, String name, byte[] bytes) {
        return multipart(API + "/products/" + productId + "/images").file(new MockMultipartFile("file", name, "image/png", bytes));
    }

    /** 上传 n 张图片，返回任务 ID */
    private long uploadImages(String token, long productId, int n) throws Exception {
        for (int i = 0; i < n; i++) {
            ok(call(upload(productId, "img-" + i + ".png", PNG), token));
        }
        return ok(call(get(API + "/products/" + productId), token)).path("id").asLong();
    }

    /** 5 张图片、脚本 4 个镜头 16 秒，可以加入队列 */
    private long readyTask(String token, long productId) throws Exception {
        long id = uploadImages(token, productId, 5);
        ok(call(json(put(API + "/" + id + "/script"), scriptBody(materialIds(id).subList(0, 4), 10)), token));
        return id;
    }

    private List<Long> materialIds(long taskId) {
        return jdbc.queryForList("select attachment_id from product_video_material where task_id = ? and deleted_at is null order by sort, id",
                Long.class, taskId);
    }

    private String materialsBody(List<Long> ids, int used) {
        StringBuilder sb = new StringBuilder("{\"items\":[");
        for (int i = 0; i < ids.size(); i++) {
            sb.append(i == 0 ? "" : ",").append("{\"attachmentId\":").append(ids.get(i)).append(",\"used\":").append(i < used).append('}');
        }
        return sb.append("]}").toString();
    }

    private String scriptBody(List<Long> ids, int words) {
        StringBuilder sb = new StringBuilder("{\"title\":\"ITVBrand V-1 Servo Drive\",\"shots\":[");
        for (int i = 0; i < ids.size(); i++) {
            sb.append(i == 0 ? "" : ",").append("{\"attachmentId\":").append(ids.get(i)).append(",\"caption\":\"")
                    .append(String.join(" ", java.util.Collections.nCopies(words, "word"))).append("\"}");
        }
        return sb.append("]}").toString();
    }

    /** 模拟队列认领开始生成（全服务器只有一个生成中） */
    private void claim(long id) {
        assertEquals(1, TenantScope.platform(() -> taskMapper.claim(id, LocalDateTime.now())));
    }

    private int status(long id) {
        return jdbc.queryForObject("select status from product_video_task where id = ?", Integer.class, id);
    }

    private MockMultipartHttpServletRequestBuilder resultRequest(long id, int version, String token) {
        MockMultipartHttpServletRequestBuilder r = multipart(GEN + id + "/result");
        r.file(new MockMultipartFile("portrait", "V-1_9x16.mp4", "video/mp4", MP4));
        r.file(new MockMultipartFile("portraitCover", "V-1_9x16.jpg", "image/jpeg", JPG));
        r.file(new MockMultipartFile("landscape", "V-1_16x9.mp4", "video/mp4", MP4));
        r.file(new MockMultipartFile("landscapeCover", "V-1_16x9.jpg", "image/jpeg", JPG));
        r.param("version", String.valueOf(version)).param("status", "success")
                .param("portraitDuration", "17").param("landscapeDuration", "17");
        r.header(VideoGeneratorClient.TOKEN_HEADER, token);
        return r;
    }

    /** 回调在另一个线程里执行，没有租户上下文与登录，与生成服务的真实回调一致 */
    private void callbackInThread(MockMultipartHttpServletRequestBuilder req) throws Exception {
        AtomicReference<Object> out = new AtomicReference<>();
        Thread t = new Thread(() -> {
            try {
                assertEquals(null, TenantContext.getTenantId());
                out.set(objectMapper.readTree(mvc.perform(req).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)));
            } catch (Exception e) {
                out.set(e);
            }
        });
        t.start();
        t.join();
        if (out.get() instanceof Exception e) {
            throw e;
        }
        ok((JsonNode) out.get());
    }

    private VideoQueueJob job(VideoGeneratorClient.Outcome outcome, List<VideoGeneratorClient.Request> sent) {
        VideoGeneratorClient fake = new VideoGeneratorClient(objectMapper, "http://video-generator", TOKEN) {
            @Override
            public Submit submit(Request req) {
                sent.add(req);
                return new Submit(outcome, outcome.name());
            }
        };
        return new VideoQueueJob(taskMapper, attachmentMapper, tasks, results, fake, settings, redis, "http://backend:8080");
    }

    private int imageAssets(int tenant) {
        return jdbc.queryForObject("select count(*) from media_item where tenant_id = ? and category = 12 and deleted_at is null",
                Integer.class, tenant);
    }

    private int videoAssets(int tenant) {
        return jdbc.queryForObject("select count(*) from media_item where tenant_id = ? and category = 13 and for_listing = 1 "
                + "and deleted_at is null", Integer.class, tenant);
    }

    private List<Long> videoAssetSources(int tenant) {
        return jdbc.queryForList("select source_id from media_item where tenant_id = ? and category = 13 and deleted_at is null order by source_id",
                Long.class, tenant);
    }

    private static List<Long> ids(JsonNode arr) {
        List<Long> out = new ArrayList<>();
        arr.forEach(n -> out.add(n.path("id").asLong()));
        out.sort(Long::compare);
        return out;
    }
}
