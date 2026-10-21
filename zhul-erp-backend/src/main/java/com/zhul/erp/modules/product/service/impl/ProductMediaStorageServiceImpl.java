package com.zhul.erp.modules.product.service.impl;

import com.zhul.erp.framework.storage.FileStorageService;
import com.zhul.erp.framework.storage.StorageArea;
import com.zhul.erp.framework.storage.UploadRules;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.product.constants.ProductErrorCodes;
import com.zhul.erp.modules.product.service.ProductMediaStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.UncheckedIOException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class ProductMediaStorageServiceImpl implements ProductMediaStorageService {

    static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
    static final long MAX_VIDEO_BYTES = 100L * 1024 * 1024;

    /** 扩展名 → 存储时使用的规范扩展名（jpeg 统一存为 jpg） */
    private static final Map<String, String> IMAGE_EXT = Map.of("jpg", "jpg", "jpeg", "jpg", "png", "png", "webp", "webp");
    private static final Map<String, String> VIDEO_EXT = Map.of("mp4", "mp4", "webm", "webm");
    private static final Set<String> REJECTED_EXT = Set.of("svg", "svgz");

    private final FileStorageService files;

    public ProductMediaStorageServiceImpl(FileStorageService files) {
        this.files = files;
    }

    @Override
    public StoredMedia store(MultipartFile file, int mediaType, String brand, String model) {
        if (file == null || file.isEmpty()) {
            throw BizException.of(ProductErrorCodes.MEDIA_FILE_INVALID, "请选择要上传的文件");
        }
        boolean image = mediaType == MEDIA_IMAGE;
        if (!image && mediaType != MEDIA_VIDEO) {
            throw BizException.of(ProductErrorCodes.PARAM_INVALID, "媒体类型只能是 1（图片）或 2（视频）");
        }
        String ext = extensionOf(file.getOriginalFilename());
        if (REJECTED_EXT.contains(ext)) {
            throw BizException.of(ProductErrorCodes.MEDIA_FILE_INVALID, "不支持 SVG 文件");
        }
        String storedExt = (image ? IMAGE_EXT : VIDEO_EXT).get(ext);
        if (storedExt == null) {
            throw BizException.of(ProductErrorCodes.MEDIA_FILE_INVALID,
                    image ? "图片仅支持 jpg、jpeg、png、webp 格式" : "视频仅支持 mp4、webm 格式");
        }
        if (file.getSize() > (image ? MAX_IMAGE_BYTES : MAX_VIDEO_BYTES)) {
            throw BizException.of(ProductErrorCodes.MEDIA_TOO_LARGE,
                    image ? "图片不能超过 5MB" : "视频不能超过 100MB");
        }
        if (!storedExt.equals(UploadRules.detect(UploadRules.head(file), file.getOriginalFilename()))) {
            throw BizException.of(ProductErrorCodes.MEDIA_FILE_INVALID, "文件内容与扩展名不一致，已拒绝");
        }
        FileStorageService.StoredFile s;
        try {
            s = files.storeForModel(StorageArea.PRODUCT, 0, brand, model, file, storedExt);
        } catch (UncheckedIOException e) {
            log.error("商品媒体文件保存失败", e);
            throw BizException.of(ProductErrorCodes.MEDIA_FILE_INVALID, "文件保存失败，请稍后重试");
        }
        return new StoredMedia(files.publicUrl(s.storage(), s.fileKey()), s.fileSize(), s.storage(), s.fileKey());
    }

    @Override
    public void deleteQuietly(String storage, String fileKey) {
        if (fileKey == null || fileKey.isEmpty()) {
            return;
        }
        files.deleteQuietly(storage, fileKey, true);
    }

    @Override
    public String publicUrl(String storage, String fileKey) {
        return files.publicUrl(storage, fileKey);
    }

    private static String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        // 只取最后一个点之后的部分，且忽略路径分隔符之前的内容；扩展名统一转小写（PNG → png）
        String name = filename.substring(Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\')) + 1);
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
