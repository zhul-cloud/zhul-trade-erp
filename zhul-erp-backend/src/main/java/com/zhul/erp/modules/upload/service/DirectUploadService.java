package com.zhul.erp.modules.upload.service;

import com.aliyun.oss.model.PartETag;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.security.SecurityUtils;
import com.zhul.erp.framework.storage.FileStorage;
import com.zhul.erp.framework.storage.FileStorageService;
import com.zhul.erp.framework.storage.FileStorages;
import com.zhul.erp.framework.storage.ImageCompressor;
import com.zhul.erp.framework.storage.LocalFileStorage;
import com.zhul.erp.framework.storage.OssFileStorage;
import com.zhul.erp.framework.storage.StorageKeys;
import com.zhul.erp.framework.storage.UploadRules;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.upload.dto.CompleteUploadRequest;
import com.zhul.erp.modules.upload.dto.PartUrlVO;
import com.zhul.erp.modules.upload.dto.PrepareUploadRequest;
import com.zhul.erp.modules.upload.dto.PrepareUploadVO;
import com.zhul.erp.modules.upload.dto.UploadedFileVO;
import com.zhul.erp.modules.upload.support.UploadScene;
import com.zhul.erp.modules.upload.support.UploadSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 大文件分片直传（spec system/file-attachment「大文件分片直传」等）：
 * 存储方式为 OSS 时浏览器把分片直接 PUT 到签名地址；为本地时 PUT 到后端分片接口，按流写入临时文件的对应位置。
 * 完成后按实际大小与文件内容校验，校验通过的文件仍留在临时位置；业务单据保存时凭返回的临时文件键认领，
 * 才转到正式文件键。传完却没保存到单据的文件和其他未完成的上传一样，1 天后由 OSS 生命周期规则或本地清理任务删除。
 */
@Slf4j
@Service
public class DirectUploadService {

    /** 每片 16MB：远小于网站入口单次 100MB 的限制，本地存储经过入口也能传 */
    public static final long PART_SIZE = 16L * 1024 * 1024;
    /** 一次签发的分片地址数 */
    static final int PRESIGN_BATCH = 8;
    static final Duration PRESIGN_TTL = Duration.ofMinutes(15);
    static final Duration SESSION_TTL = Duration.ofDays(1);
    /** 本地存储上传前要留出的余量 */
    static final long LOCAL_RESERVE_BYTES = 1024L * 1024 * 1024;
    private static final int HEAD_BYTES = 512;
    private static final String KEY_PREFIX = "zhul:erp:upload:";
    private static final String UPLOAD_API = "/api/v1/uploads/";
    private static final String TMP_PREFIX = "tmp/";
    /** 临时文件键：tmp/{租户}/{会话ID}.{扩展名} */
    private static final Pattern TMP_KEY = Pattern.compile("tmp/(\\d+)/([0-9a-f]{32})\\.([a-z0-9]{1,8})");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Map<String, UploadScene> scenes = new LinkedHashMap<>();
    private final FileStorages storages;
    private final FileStorageService fileStorage;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public DirectUploadService(List<UploadScene> sceneList, FileStorages storages, FileStorageService fileStorage,
                               StringRedisTemplate redis, ObjectMapper objectMapper) {
        sceneList.forEach(s -> scenes.put(s.key(), s));
        this.storages = storages;
        this.fileStorage = fileStorage;
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    // ---------------------------------------------------------------- 准备

    public PrepareUploadVO prepare(PrepareUploadRequest req) {
        UploadScene scene = scene(req.getScene());
        if (!scene.allowed().getAsBoolean()) {
            throw new AccessDeniedException("没有上传权限");
        }
        long size = req.getSize();
        if (size > scene.maxBytes()) {
            throw new BizException("单个文件不能超过 " + UploadScene.sizeText(scene.maxBytes()));
        }
        String ext = extOfName(req.getFileName());
        if (ext == null || !scene.exts().contains(ext)) {
            throw new BizException(scene.typeMessage());
        }
        long existing = req.getExistingBytes() == null ? 0 : req.getExistingBytes();
        if (scene.totalBytes() > 0 && existing + size > scene.totalBytes()) {
            throw new BizException("附件合计不能超过 " + UploadScene.sizeText(scene.totalBytes()));
        }

        UploadSession s = new UploadSession();
        s.setId(UUID.randomUUID().toString().replace("-", ""));
        s.setTenantId(tenantId());
        s.setUsername(SecurityUtils.getCurrentUsername());
        s.setScene(scene.key());
        s.setFileName(UploadRules.cleanFileName(req.getFileName(), ext));
        s.setExt(ext);
        s.setSize(size);
        s.setPartSize(PART_SIZE);
        s.setPartCount((int) ((size + PART_SIZE - 1) / PART_SIZE));
        s.setTmpKey(TMP_PREFIX + s.getTenantId() + "/" + s.getId() + "." + ext);
        FileStorage current = storages.current();
        if (current instanceof OssFileStorage oss) {
            s.setStorage(FileStorage.OSS);
            s.setOssUploadId(oss.initiateMultipart(s.getTmpKey(), UploadRules.contentTypeOf(ext)));
        } else {
            s.setStorage(FileStorage.LOCAL);
            s.setToken(HexFormat.of().formatHex(randomBytes()));
            createLocalTmp(s);
        }
        save(s);
        log.info("准备直传，uploadId={}，scene={}，size={}，storage={}", s.getId(), s.getScene(), size, s.getStorage());
        return new PrepareUploadVO(s.getId(), mode(s), PART_SIZE, s.getPartCount(), urls(s, 1, PRESIGN_BATCH));
    }

    /** 续签：领取从 from 开始的 count 个分片地址 */
    public List<PartUrlVO> partUrls(String id, int from, int count) {
        UploadSession s = mine(id);
        return urls(s, from, Math.max(1, Math.min(count, PRESIGN_BATCH)));
    }

    // ---------------------------------------------------------------- 本地分片

    /** 本地存储时接收一个分片：按流写到临时文件的对应位置，不把整片读进内存；乱序、重复上传同一片都正确 */
    public String receivePart(String id, int n, String token, InputStream body) {
        UploadSession s = mine(id);
        if (!FileStorage.LOCAL.equals(s.getStorage())) {
            throw new BizException("这次上传不走本地分片接口");
        }
        if (token == null || !token.equals(s.getToken())) {
            throw new AccessDeniedException("上传地址无效或已过期");
        }
        checkPartNumber(s, n);
        long offset = (n - 1L) * s.getPartSize();
        long expected = partLength(s, n);
        Path tmp = local().privatePath(s.getTmpKey());
        long written = 0;
        byte[] buf = new byte[64 * 1024];
        try (FileChannel ch = FileChannel.open(tmp, StandardOpenOption.WRITE)) {
            int r;
            while ((r = body.read(buf)) != -1) {
                if (written + r > expected) {
                    throw new BizException("分片大小不正确，请重新上传");
                }
                ByteBuffer bb = ByteBuffer.wrap(buf, 0, r);
                while (bb.hasRemaining()) {
                    ch.write(bb, offset + written + bb.position());
                }
                written += r;
            }
        } catch (IOException e) {
            throw new UncheckedIOException("保存分片失败", e);
        }
        if (written != expected) {
            throw new BizException("分片没有传完整，请重新上传");
        }
        redis.opsForSet().add(partsKey(s), String.valueOf(n));
        redis.expire(partsKey(s), SESSION_TTL);
        return "local-" + n;
    }

    // ---------------------------------------------------------------- 完成与取消

    public UploadedFileVO complete(String id, CompleteUploadRequest req) {
        UploadSession s = mine(id);
        UploadScene scene = scene(s.getScene());
        boolean oss = FileStorage.OSS.equals(s.getStorage());
        if (oss) {
            List<PartETag> parts = new ArrayList<>(s.getPartCount());
            if (req.getParts() != null) {
                req.getParts().stream()
                        .filter(p -> p.getN() != null && p.getEtag() != null && !p.getEtag().isBlank())
                        .sorted((a, b) -> Integer.compare(a.getN(), b.getN()))
                        .forEach(p -> parts.add(new PartETag(p.getN(), p.getEtag())));
            }
            if (parts.size() != s.getPartCount() || parts.stream().map(PartETag::getPartNumber).distinct().count() != s.getPartCount()) {
                throw new BizException("还有分片没有上传完");
            }
            ossStorage().completeMultipart(s.getTmpKey(), s.getOssUploadId(), parts);
        } else {
            Long got = redis.opsForSet().size(partsKey(s));
            if (got == null || got != s.getPartCount()) {
                throw new BizException("还有分片没有上传完");
            }
        }
        try {
            verify(s, scene, oss);
        } catch (RuntimeException e) {
            // 校验没通过：删除文件与会话，这次上传作废
            removeTmp(s, oss);
            redis.delete(List.of(sessionKey(s.getTenantId(), s.getId()), partsKey(s)));
            throw e;
        }
        redis.delete(partsKey(s));
        save(s);
        log.info("直传完成，待保存单据时认领，uploadId={}，size={}", s.getId(), s.getFileSize());
        return new UploadedFileVO(s.getStorage(), s.getTmpKey(), s.getFileName(), s.getFileSize(), s.getContentType());
    }

    // ---------------------------------------------------------------- 认领

    /** 文件键是否为直传完成、还没认领的临时文件 */
    public static boolean isPending(String fileKey) {
        return fileKey != null && fileKey.startsWith(TMP_PREFIX);
    }

    /** 待认领文件的实际大小（用于保存前的合计校验）；不是本租户、本人在该场景上传并通过校验的文件时拒绝 */
    public long pendingSize(String fileKey, String sceneKey) {
        return pending(fileKey, sceneKey).getFileSize();
    }

    /**
     * 业务单据保存时认领：把临时文件转到正式文件键并返回它。同一文件再次认领（如保存失败后重试）返回同一个键；
     * 临时文件已被清理（超过 1 天）时提示重新上传。
     */
    public String claim(String fileKey, String sceneKey) {
        UploadSession s = pending(fileKey, sceneKey);
        if (s.getClaimedKey() != null) {
            return s.getClaimedKey();
        }
        UploadScene scene = scene(s.getScene());
        String key = StorageKeys.newKey(scene.area(), s.getTenantId(), s.getExt());
        if (FileStorage.OSS.equals(s.getStorage())) {
            OssFileStorage oss = ossStorage();
            if (oss.size(false, s.getTmpKey()) < 0) {
                throw new BizException("附件「" + s.getFileName() + "」上传已过期，请重新上传");
            }
            oss.copy(false, s.getTmpKey(), key);
            removeTmp(s, true);
        } else {
            Path source = local().privatePath(s.getTmpKey());
            Path target = local().privatePath(key);
            try {
                if (!Files.exists(source)) {
                    throw new BizException("附件「" + s.getFileName() + "」上传已过期，请重新上传");
                }
                Files.createDirectories(target.getParent());
                Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                throw new UncheckedIOException("保存文件失败", e);
            }
        }
        s.setClaimedKey(key);
        save(s);
        log.info("认领直传文件，uploadId={}，fileKey={}", s.getId(), key);
        return key;
    }

    private UploadSession pending(String fileKey, String sceneKey) {
        Matcher m = fileKey == null ? null : TMP_KEY.matcher(fileKey);
        if (m == null || !m.matches() || Integer.parseInt(m.group(1)) != tenantId()) {
            throw new BizException("附件不存在或已过期，请重新上传");
        }
        UploadSession s = mine(m.group(2));
        if (!s.isVerified() || !fileKey.equals(s.getTmpKey()) || !s.getScene().equals(sceneKey)) {
            throw new BizException("附件不存在或已过期，请重新上传");
        }
        return s;
    }

    /** 取消：中止分片上传、删除临时文件与会话 */
    public void abort(String id) {
        UploadSession s = mine(id);
        boolean oss = FileStorage.OSS.equals(s.getStorage());
        if (oss) {
            ossStorage().abortMultipart(s.getTmpKey(), s.getOssUploadId());
        }
        removeTmp(s, oss);
        redis.delete(List.of(sessionKey(s.getTenantId(), s.getId()), partsKey(s)));
    }

    // ---------------------------------------------------------------- 内部

    /**
     * 核对实际大小与文件内容；图片照常压缩（spec system/file-attachment「上传压缩」），压缩后的文件写回临时位置。
     * 通过后在会话里记下实际大小与类型，文件留在临时位置等认领。
     */
    private void verify(UploadSession s, UploadScene scene, boolean oss) {
        long actual = oss ? ossStorage().size(false, s.getTmpKey()) : localSize(s);
        if (actual != s.getSize() || actual > scene.maxBytes()) {
            throw new BizException("文件大小与声明不一致，已删除，请重新上传");
        }
        byte[] head = oss ? ossStorage().readHead(s.getTmpKey(), HEAD_BYTES) : localHead(s);
        String ext = UploadRules.detect(head, s.getFileName());
        if (ext == null || !scene.exts().contains(ext)) {
            throw new BizException(scene.typeMessage() + "，文件已删除");
        }
        long size = actual;
        if (UploadRules.isImage(ext)) {
            Path raw = fileStorage.download(s.getStorage(), s.getTmpKey());
            Path compressed = null;
            try {
                ImageCompressor.Result r = ImageCompressor.compress(raw, ext, fileStorage.workDir());
                compressed = r.file();
                String key = TMP_PREFIX + s.getTenantId() + "/" + s.getId() + "." + r.ext();
                (oss ? ossStorage() : local()).put(false, key, r.file(), UploadRules.contentTypeOf(r.ext()));
                if (!key.equals(s.getTmpKey())) {
                    removeTmp(s, oss);
                    s.setTmpKey(key);
                }
                if (!r.ext().equals(ext)) {
                    s.setFileName(UploadRules.withExt(s.getFileName(), r.ext()));
                }
                ext = r.ext();
                size = Files.size(r.file());
            } catch (IOException e) {
                throw new UncheckedIOException("处理图片失败", e);
            } finally {
                FileStorageService.deleteTemp(raw);
                FileStorageService.deleteTemp(compressed);
            }
        }
        s.setExt(ext);
        s.setFileSize(size);
        s.setContentType(UploadRules.contentTypeOf(ext));
        s.setVerified(true);
    }

    private List<PartUrlVO> urls(UploadSession s, int from, int count) {
        List<PartUrlVO> list = new ArrayList<>(count);
        Instant exp = Instant.now().plus(PRESIGN_TTL);
        for (int n = Math.max(1, from); n < from + count && n <= s.getPartCount(); n++) {
            String url = FileStorage.OSS.equals(s.getStorage())
                    ? ossStorage().presignPart(s.getTmpKey(), s.getOssUploadId(), n, partLength(s, n), exp)
                    : UPLOAD_API + s.getId() + "/parts/" + n + "?t=" + s.getToken();
            list.add(new PartUrlVO(n, url));
        }
        return list;
    }

    /** 准备上传时检查剩余空间，避免传到一半写满磁盘；创建临时文件 */
    private void createLocalTmp(UploadSession s) {
        Path tmp = local().privatePath(s.getTmpKey());
        try {
            Files.createDirectories(tmp.getParent());
            if (Files.getFileStore(tmp.getParent()).getUsableSpace() < s.getSize() + LOCAL_RESERVE_BYTES) {
                throw new BizException("服务器存储空间不足，请联系管理员");
            }
            Files.createFile(tmp);
        } catch (IOException e) {
            throw new UncheckedIOException("创建上传临时文件失败", e);
        }
    }

    private long localSize(UploadSession s) {
        try {
            return Files.size(local().privatePath(s.getTmpKey()));
        } catch (IOException e) {
            throw new UncheckedIOException("读取上传文件失败", e);
        }
    }

    private byte[] localHead(UploadSession s) {
        try (InputStream in = Files.newInputStream(local().privatePath(s.getTmpKey()))) {
            return in.readNBytes(HEAD_BYTES);
        } catch (IOException e) {
            throw new UncheckedIOException("读取上传文件失败", e);
        }
    }

    private void removeTmp(UploadSession s, boolean oss) {
        try {
            if (oss) {
                ossStorage().delete(false, s.getTmpKey());
            } else {
                Files.deleteIfExists(local().privatePath(s.getTmpKey()));
            }
        } catch (IOException | RuntimeException e) {
            // 残留的临时文件由 OSS 生命周期规则或本地清理任务删除
            log.warn("删除直传临时文件失败，uploadId={}：{}", s.getId(), e.toString());
        }
    }

    /** 第 n 片的字节数：最后一片是余下的部分 */
    static long partLength(UploadSession s, int n) {
        return Math.min(s.getPartSize(), s.getSize() - (n - 1L) * s.getPartSize());
    }

    private void checkPartNumber(UploadSession s, int n) {
        if (n < 1 || n > s.getPartCount()) {
            throw new BizException("分片编号不正确");
        }
    }

    /** 只能操作本租户、本人的上传会话；过期或不存在时提示重新上传 */
    private UploadSession mine(String id) {
        if (id == null || !id.matches("[0-9a-f]{32}")) {
            throw new BizException("上传已过期，请重新上传");
        }
        String json = redis.opsForValue().get(sessionKey(tenantId(), id));
        if (json == null) {
            throw new BizException("上传已过期，请重新上传");
        }
        UploadSession s;
        try {
            s = objectMapper.readValue(json, UploadSession.class);
        } catch (JsonProcessingException e) {
            throw new BizException("上传已过期，请重新上传");
        }
        if (!SecurityUtils.getCurrentUsername().equals(s.getUsername())) {
            throw new AccessDeniedException("不能操作别人的上传");
        }
        return s;
    }

    private void save(UploadSession s) {
        try {
            redis.opsForValue().set(sessionKey(s.getTenantId(), s.getId()), objectMapper.writeValueAsString(s), SESSION_TTL);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("保存上传会话失败", e);
        }
    }

    private UploadScene scene(String key) {
        UploadScene scene = key == null ? null : scenes.get(key);
        if (scene == null) {
            throw new BizException("不支持的上传类型");
        }
        return scene;
    }

    /** 按文件名判断扩展名（jpeg 视为 jpg）；完成时再按内容核对 */
    static String extOfName(String fileName) {
        if (fileName == null) {
            return null;
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return null;
        }
        String ext = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        return "jpeg".equals(ext) ? "jpg" : ext;
    }

    private static String mode(UploadSession s) {
        return FileStorage.OSS.equals(s.getStorage()) ? "oss" : "local";
    }

    static String sessionKey(int tenantId, String id) {
        return KEY_PREFIX + tenantId + ":" + id;
    }

    private static String partsKey(UploadSession s) {
        return sessionKey(s.getTenantId(), s.getId()) + ":parts";
    }

    private OssFileStorage ossStorage() {
        return storages.oss().orElseThrow(() -> new BizException("文件存储没有配置 OSS，请联系管理员"));
    }

    private LocalFileStorage local() {
        return storages.local();
    }

    private static int tenantId() {
        Integer t = TenantContext.getTenantId();
        if (t == null) {
            throw new AccessDeniedException("缺少租户");
        }
        return t;
    }

    private static byte[] randomBytes() {
        byte[] b = new byte[16];
        RANDOM.nextBytes(b);
        return b;
    }

    /** 已登记的场景（供启动检查与测试） */
    public Set<String> sceneKeys() {
        return scenes.keySet();
    }
}
