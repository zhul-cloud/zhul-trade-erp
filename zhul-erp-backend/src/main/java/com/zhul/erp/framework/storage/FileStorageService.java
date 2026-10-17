package com.zhul.erp.framework.storage;

import com.zhul.erp.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/**
 * 业务代码使用的文件存储：上传（校验、图片压缩、按环节生成文件键）、校验提交的文件键、签发访问链接、读取与复制。
 * 业务记录只保存「存储方式 + 文件键」；新文件写当前存储方式，读取按记录上的存储方式。
 */
@Slf4j
@Component
public class FileStorageService {

    private static final String MISSING = "附件不存在，请重新上传";

    private final FileStorages storages;
    private final StorageProperties props;
    private final Path workDir;

    public FileStorageService(FileStorages storages, StorageProperties props) {
        this.storages = storages;
        this.props = props;
        try {
            this.workDir = Files.createDirectories(Path.of(System.getProperty("java.io.tmpdir"), "zhul-storage"));
        } catch (IOException e) {
            throw new UncheckedIOException("创建文件处理临时目录失败", e);
        }
    }

    /** 已保存文件：业务单据提交 fileKey 后才算生效 */
    public record StoredFile(String storage, String fileKey, String fileName, long fileSize, String contentType) {
        public boolean video() {
            return UploadRules.isVideo(UploadRules.extOf(fileKey));
        }
    }

    /** 私有文件的临时访问链接 */
    public record FileLink(String url, String fileName, Instant expiresAt) {
    }

    // ---------- 上传 ----------

    /** 校验类型与大小后保存到某个业务环节 */
    public StoredFile store(StorageArea area, int tenantId, MultipartFile file, Set<String> allowedExts,
                            long maxBytes, String typeMessage) {
        String ext = UploadRules.check(file, allowedExts, maxBytes, typeMessage);
        return save(area, tenantId, null, null, file, ext);
    }

    /** 调用方已自行校验（如商品媒体、站点外观有各自的格式规则），ext 为识别出的扩展名 */
    public StoredFile storeValidated(StorageArea area, int tenantId, MultipartFile file, String ext) {
        return save(area, tenantId, null, null, file, ext);
    }

    /** 按「品牌 / 型号」归档（共享商品库），调用方已校验 */
    public StoredFile storeForModel(StorageArea area, int tenantId, String brand, String model, MultipartFile file, String ext) {
        return save(area, tenantId, brand, model, file, ext);
    }

    private StoredFile save(StorageArea area, int tenantId, String brand, String model, MultipartFile file, String ext) {
        Path upload = null;
        Path compressed = null;
        try {
            upload = Files.createTempFile(workDir, "up-", "." + ext);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, upload, StandardCopyOption.REPLACE_EXISTING);
            }
            // 大小上限按声明的大小校验过；实际字节多于声明时拒绝（不能信任客户端声明）
            if (Files.size(upload) > file.getSize()) {
                throw new BizException("文件大小与声明不一致，已拒绝");
            }
            Path source = upload;
            String finalExt = ext;
            if (UploadRules.isImage(ext)) {
                ImageCompressor.Result r = ImageCompressor.compress(upload, ext, workDir);
                compressed = r.file();
                source = r.file();
                finalExt = r.ext();
            }
            String key = brand == null && model == null
                    ? StorageKeys.newKey(area, tenantId, finalExt)
                    : StorageKeys.modelKey(area, tenantId, brand, model, finalExt);
            String contentType = UploadRules.contentTypeOf(finalExt);
            FileStorage fs = storages.current();
            fs.put(area.publicRead(), key, source, contentType);
            String name = UploadRules.cleanFileName(file.getOriginalFilename(), ext);
            if (!finalExt.equals(ext)) {
                name = UploadRules.withExt(name, finalExt);
            }
            return new StoredFile(fs.code(), key, name, Files.size(source), contentType);
        } catch (IOException e) {
            throw new UncheckedIOException("保存上传文件失败", e);
        } finally {
            deleteTemp(upload);
            deleteTemp(compressed);
        }
    }

    // ---------- 提交单据时校验文件键 ----------

    /**
     * 业务单据提交的文件键：必须属于该租户、该环节，且文件存在。返回文件所在的存储方式
     * （刚上传的在当前存储方式；迁移前上传、尚未迁移的旧键在本地）。
     */
    public String locate(StorageArea area, String key, int tenantId) {
        if (!StorageKeys.owned(area, key, tenantId)) {
            throw new BizException(MISSING);
        }
        FileStorage current = storages.current();
        if (current.size(area.publicRead(), key) >= 0) {
            return current.code();
        }
        if (current != storages.local() && storages.local().size(area.publicRead(), key) >= 0) {
            return FileStorage.LOCAL;
        }
        throw new BizException(MISSING);
    }

    // ---------- 读取 ----------

    public InputStream open(String storage, String key) {
        return storages.of(storage).open(false, key);
    }

    public byte[] readAll(String storage, String key) {
        try (InputStream in = open(storage, key)) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("读取文件失败", e);
        }
    }

    /** 下载到临时文件（给需要真实文件的处理用，如 LibreOffice 转换、Excel 解析），用完由调用方删除 */
    public Path download(String storage, String key) {
        try (InputStream in = open(storage, key)) {
            Path tmp = Files.createTempFile(workDir, "dl-", "." + UploadRules.extOf(key));
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            return tmp;
        } catch (IOException e) {
            throw new UncheckedIOException("读取文件失败", e);
        }
    }

    /** 私有文件的临时访问链接，有效期按配置（默认 10 分钟） */
    public FileLink link(String storage, String key, String fileName, boolean download) {
        return link(storage, key, fileName, download, Duration.ofSeconds(props.getSignedUrlTtlSeconds()));
    }

    public FileLink link(String storage, String key, String fileName, boolean download, Duration ttl) {
        Instant exp = Instant.now().plus(ttl);
        return new FileLink(storages.of(storage).signedUrl(key, fileName, exp, download), fileName, exp);
    }

    public String publicUrl(String storage, String key) {
        return storages.of(storage).publicUrl(key);
    }

    // ---------- 复制、移动、删除 ----------

    /** 把一个已有私有文件复制到另一个环节（如商机附件转到客户询盘），写入当前存储方式 */
    public StoredFile copy(String storage, String key, StorageArea area, int tenantId, String fileName) {
        Path tmp = download(storage, key);
        try {
            String ext = UploadRules.extOf(key);
            String newKey = StorageKeys.newKey(area, tenantId, ext);
            String contentType = UploadRules.contentTypeOf(ext);
            FileStorage fs = storages.current();
            fs.put(area.publicRead(), newKey, tmp, contentType);
            return new StoredFile(fs.code(), newKey, UploadRules.cleanFileName(fileName, ext), Files.size(tmp), contentType);
        } catch (IOException e) {
            throw new UncheckedIOException("复制附件失败", e);
        } finally {
            deleteTemp(tmp);
        }
    }

    /** 实拍素材挂到任务后移到「品牌 / 型号」目录（同一存储内），返回新文件键 */
    public String moveToModel(String storage, String key, int tenantId, String brand, String model) {
        String newKey = StorageKeys.modelKey(StorageArea.MEDIA, tenantId, brand, model, UploadRules.extOf(key));
        FileStorage fs = storages.of(storage);
        fs.copy(false, key, newKey);
        fs.delete(false, key);
        return newKey;
    }

    /** 删除文件，失败只记日志（业务记录已软删除，残留文件可由清理任务处理） */
    public void deleteQuietly(String storage, String key, boolean publicRead) {
        try {
            storages.of(storage).delete(publicRead, key);
        } catch (RuntimeException e) {
            log.warn("删除文件失败，key={}：{}", key, e.toString());
        }
    }

    public FileStorages storages() {
        return storages;
    }

    public Path workDir() {
        return workDir;
    }

    public static void deleteTemp(Path p) {
        if (p == null) {
            return;
        }
        try {
            Files.deleteIfExists(p);
        } catch (IOException e) {
            log.warn("删除临时文件失败：{}", p);
        }
    }
}
