package com.zhul.erp.modules.upload;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhul.erp.framework.storage.FileStorages;
import com.zhul.erp.modules.inquiry.InquiryContractSupport;
import com.zhul.erp.modules.upload.service.DirectUploadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * 大文件分片直传（spec system/file-attachment「上传场景」「大文件分片直传」「直传完成校验」「本地存储下的直传」）
 * 与询盘大附件（spec inquiry/inquiry-intake「询盘大附件」）。测试环境存储方式为本地，分片走后端接口。
 */
class DirectUploadContractTest extends InquiryContractSupport {

    private static final String UP = "/api/v1/uploads";
    private static final String SCENE = "inquiry-attachment";
    private static final int PART = (int) DirectUploadService.PART_SIZE;

    @Autowired private FileStorages storages;
    private String admin;

    @BeforeEach
    void login() {
        loginAsAdmin("it_up_admin");
        admin = token("it_up_admin");
    }

    private static byte[] pdf(int size) {
        byte[] b = new byte[size];
        Arrays.fill(b, (byte) 'x');
        System.arraycopy("%PDF-1.7\n".getBytes(), 0, b, 0, 9);
        return b;
    }

    private JsonNode prepare(String name, long size, Long existing, String token) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("scene", SCENE);
        body.put("fileName", name);
        body.put("size", size);
        if (existing != null) {
            body.put("existingBytes", existing);
        }
        return call(json(post(UP), write(body)), token);
    }

    private JsonNode putPart(JsonNode prep, int n, byte[] data, String token) throws Exception {
        String url = prep.path("parts").get(n - 1).path("url").asText();
        return call(put(url).content(data), token);
    }

    private static byte[] slice(byte[] all, int n) {
        int from = (n - 1) * PART;
        return Arrays.copyOfRange(all, from, Math.min(all.length, from + PART));
    }

    private JsonNode complete(String id, int parts, String token) throws Exception {
        List<Map<String, Object>> list = new ArrayList<>();
        for (int n = 1; n <= parts; n++) {
            list.add(Map.of("n", n, "etag", "local-" + n));
        }
        return call(json(post(UP + "/" + id + "/complete"), write(Map.of("parts", list))), token);
    }

    /** 走完整个直传流程，返回完成后的文件 */
    private JsonNode upload(String name, byte[] data) throws Exception {
        JsonNode prep = ok(prepare(name, data.length, null, admin));
        int count = prep.path("partCount").asInt();
        for (int n = 1; n <= count; n++) {
            ok(putPart(prep, n, slice(data, n), admin));
        }
        return ok(complete(prep.path("uploadId").asText(), count, admin));
    }

    private Path privatePath(String key) {
        return storages.local().privatePath(key);
    }

    @Test
    void outOfOrderAndRepeatedParts_staysInTmp_untilInquirySaveClaimsIt() throws Exception {
        byte[] data = pdf(PART + 1024 * 1024);
        JsonNode prep = ok(prepare("设备清单.pdf", data.length, null, admin));
        assertEquals("local", prep.path("mode").asText());
        assertEquals(2, prep.path("partCount").asInt());
        String id = prep.path("uploadId").asText();
        ok(putPart(prep, 2, slice(data, 2), admin));
        ok(putPart(prep, 1, slice(data, 1), admin));
        ok(putPart(prep, 1, slice(data, 1), admin));
        JsonNode file = ok(complete(id, 2, admin));

        String key = file.path("fileKey").asText();
        assertEquals("tmp/0/" + id + ".pdf", key, "校验通过后仍在临时位置，等保存单据时认领");
        assertEquals(data.length, file.path("fileSize").asLong());
        assertTrue(Arrays.equals(data, Files.readAllBytes(privatePath(key))), "分片按位置拼回原文件");

        long c = customer("Acme", "Germany");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("customerId", c);
        body.put("source", 1);
        body.put("attachments", List.of(Map.of("fileKey", key, "fileName", "设备清单.pdf")));
        long inquiry = ok(call(json(post(INQ), write(body)), admin)).path("id").asLong();
        JsonNode detail = ok(call(get(INQ + "/" + inquiry), admin));
        assertEquals(data.length, detail.path("attachments").get(0).path("fileSize").asLong());
        String saved = jdbc.queryForObject("select file_key from customer_inquiry_attachment where customer_inquiry_id = ?",
                String.class, inquiry);
        assertTrue(saved.matches("tenant/0/inquiry/\\d{6}/[0-9a-f]{32}\\.pdf"), saved);
        assertTrue(Arrays.equals(data, Files.readAllBytes(privatePath(saved))), "保存时转到正式位置");
        assertFalse(Files.exists(privatePath(key)), "临时文件已挪走");
    }

    @Test
    void pendingFile_canOnlyBeClaimedByUploader_inSameTenantAndScene() throws Exception {
        String key = upload("a.pdf", pdf(1000)).path("fileKey").asText();
        long c = customer("Gamma", "Spain");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("customerId", c);
        body.put("source", 1);

        body.put("attachments", List.of(Map.of("fileKey", key, "fileName", "a.pdf")));
        assertNotEquals(0, call(json(post(INQ), write(body)), token("it_lin")).path("code").asInt(), "别人传的文件不能认领");
        body.put("attachments", List.of(Map.of("fileKey", key.replace("tmp/0/", "tmp/1/"), "fileName", "a.pdf")));
        assertEquals("附件不存在或已过期，请重新上传", call(json(post(INQ), write(body)), admin).path("message").asText(),
                "改了租户的临时文件键不认");
        body.put("attachments", List.of(Map.of("fileKey", "tmp/0/" + "0".repeat(32) + ".pdf", "fileName", "a.pdf")));
        assertEquals("上传已过期，请重新上传", call(json(post(INQ), write(body)), admin).path("message").asText());
        assertTrue(Files.exists(privatePath(key)), "没保存的文件留在临时位置，由 1 天的清理任务删除");

        body.put("attachments", List.of(Map.of("fileKey", key, "fileName", "a.pdf")));
        ok(call(json(post(INQ), write(body)), admin));
        assertFalse(Files.exists(privatePath(key)));
    }

    @Test
    void complete_rejectsMissingPart_andShortPart() throws Exception {
        byte[] data = pdf(PART + 10);
        JsonNode prep = ok(prepare("a.pdf", data.length, null, admin));
        String id = prep.path("uploadId").asText();
        ok(putPart(prep, 1, slice(data, 1), admin));
        assertEquals("还有分片没有上传完", complete(id, 2, admin).path("message").asText());
        assertEquals("分片没有传完整，请重新上传", putPart(prep, 2, new byte[5], admin).path("message").asText());
        assertEquals("分片大小不正确，请重新上传", putPart(prep, 2, new byte[11], admin).path("message").asText());
    }

    @Test
    void disguisedFile_isDeleted_andNotUsable() throws Exception {
        byte[] exe = new byte[2048];
        exe[0] = 'M';
        exe[1] = 'Z';
        JsonNode prep = ok(prepare("报价单.pdf", exe.length, null, admin));
        String id = prep.path("uploadId").asText();
        ok(putPart(prep, 1, exe, admin));
        String msg = complete(id, 1, admin).path("message").asText();
        assertTrue(msg.startsWith("文件类型不支持") && msg.endsWith("文件已删除"), msg);
        assertFalse(Files.exists(privatePath("tmp/0/" + id + ".pdf")));
        assertEquals("上传已过期，请重新上传", complete(id, 1, admin).path("message").asText(), "会话随之作废");
    }

    @Test
    void prepare_rejectsOversize_wrongType_totalOver2gb_andNoPermission() throws Exception {
        assertEquals("单个文件不能超过 500MB", prepare("big.pdf", 650L * 1024 * 1024, null, admin).path("message").asText());
        assertEquals("文件类型不支持，只支持 JPG、PNG、XLS、XLSX、CSV、PDF",
                prepare("资料.zip", 1000, null, admin).path("message").asText());
        assertEquals("附件合计不能超过 2GB",
                prepare("a.pdf", 300L * 1024 * 1024, 1900L * 1024 * 1024, admin).path("message").asText());
        loginWithResources("it_up_nobody");
        JsonNode denied = prepare("a.pdf", 1000, null, token("it_up_nobody"));
        assertNotEquals(0, denied.path("code").asInt(), denied.toString());
    }

    @Test
    void partToken_andSession_belongToTheUploader() throws Exception {
        byte[] data = pdf(1000);
        JsonNode prep = ok(prepare("a.pdf", data.length, null, admin));
        String id = prep.path("uploadId").asText();
        JsonNode bad = call(put(UP + "/" + id + "/parts/1?t=wrong").content(data), admin);
        assertNotEquals(0, bad.path("code").asInt(), "令牌不对");
        JsonNode other = putPart(prep, 1, data, token("it_lin"));
        assertNotEquals(0, other.path("code").asInt(), "别人的上传");
        assertNotEquals(0, complete(id, 1, token("it_lin")).path("code").asInt());
    }

    @Test
    void abort_removesTempFile_andSession() throws Exception {
        byte[] data = pdf(1000);
        JsonNode prep = ok(prepare("a.pdf", data.length, null, admin));
        String id = prep.path("uploadId").asText();
        ok(putPart(prep, 1, data, admin));
        assertTrue(Files.exists(privatePath("tmp/0/" + id + ".pdf")));
        ok(call(delete(UP + "/" + id), admin));
        assertFalse(Files.exists(privatePath("tmp/0/" + id + ".pdf")));
        assertEquals("上传已过期，请重新上传", complete(id, 1, admin).path("message").asText());
    }

    @Test
    void inquiry_noCountLimit_butTotalCappedAt2gb() throws Exception {
        long c = customer("Beta", "France");
        List<Map<String, Object>> refs = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            refs.add(Map.of("fileKey", upload("清单" + i + ".pdf", pdf(200)).path("fileKey").asText(), "fileName", "清单" + i + ".pdf"));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("customerId", c);
        body.put("source", 1);
        body.put("attachments", refs);
        long id = ok(call(json(post(INQ), write(body)), admin)).path("id").asLong();
        JsonNode detail = ok(call(get(INQ + "/" + id), admin));
        assertEquals(25, detail.path("attachments").size(), "不再限 10 个");

        // 已有附件合计接近 2GB（直接改库里的大小，避免测试里真的写 2GB 文件），再补传一个就超
        long first = detail.path("attachments").get(0).path("id").asLong();
        jdbc.update("update customer_inquiry_attachment set file_size = ? where id = ?", 2L * 1024 * 1024 * 1024 - 100, first);
        List<Long> keep = new ArrayList<>();
        detail.path("attachments").forEach(a -> keep.add(a.path("id").asLong()));
        Map<String, Object> upd = new LinkedHashMap<>();
        upd.put("source", 1);
        upd.put("inquiryDate", LocalDate.now().toString());
        upd.put("quoteDeadline", LocalDate.now().toString());
        upd.put("keepAttachmentIds", keep);
        upd.put("attachments", List.of(Map.of("fileKey", upload("补充.pdf", pdf(200)).path("fileKey").asText(), "fileName", "补充.pdf")));
        assertEquals("这张询盘的附件合计不能超过 2GB", call(json(put(INQ + "/" + id), write(upd)), admin).path("message").asText());

        // 删掉那个大附件后就能补传
        keep.remove(first);
        ok(call(json(put(INQ + "/" + id), write(upd)), admin));
    }
}
