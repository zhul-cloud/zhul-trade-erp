package com.zhul.erp.modules.product.service.impl;

import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.product.constants.ProductErrorCodes;
import com.zhul.erp.modules.product.service.ProductMediaStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class ProductMediaStorageServiceImpl implements ProductMediaStorageService {

    static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
    static final long MAX_VIDEO_BYTES = 100L * 1024 * 1024;
    private static final String SUB_DIR = "product";
    private static final String URL_PREFIX = "/uploads/";
    private static final int HEADER_BYTES = 16;

    /** 扩展名 → 存盘时使用的规范扩展名（jpeg 统一存为 jpg） */
    private static final Map<String, String> IMAGE_EXT = Map.of("jpg", "jpg", "jpeg", "jpg", "png", "png", "webp", "webp");
    private static final Map<String, String> VIDEO_EXT = Map.of("mp4", "mp4", "webm", "webm");
    /** 规范扩展名 → 文件头识别出的格式，二者必须一致 */
    private static final Map<String, String> EXT_TO_FORMAT = Map.of(
            "jpg", "jpeg", "png", "png", "webp", "webp", "mp4", "mp4", "webm", "webm");
    private static final Set<String> REJECTED_EXT = Set.of("svg", "svgz");

    private final Path uploadRoot;

    public ProductMediaStorageServiceImpl(@Value("${zhul.upload.dir}") String uploadDir) {
        this.uploadRoot = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    @Override
    public StoredMedia store(MultipartFile file, int mediaType) {
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
        long maxBytes = image ? MAX_IMAGE_BYTES : MAX_VIDEO_BYTES;
        if (file.getSize() > maxBytes) {
            throw tooLarge(image);
        }

        Path target = null;
        try (InputStream in = file.getInputStream()) {
            byte[] header = in.readNBytes(HEADER_BYTES);
            if (!EXT_TO_FORMAT.get(storedExt).equals(detectFormat(header))) {
                throw BizException.of(ProductErrorCodes.MEDIA_FILE_INVALID, "文件内容与扩展名不一致，已拒绝");
            }
            Path dir = uploadRoot.resolve(SUB_DIR).resolve(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM")));
            Files.createDirectories(dir);
            // 文件名完全由服务端生成，不含任何客户端输入；仍然确认解析结果没有跳出上传目录
            target = resolveInside(uploadRoot, dir, UUID.randomUUID().toString().replace("-", "") + "." + storedExt);
            long total = copyWithLimit(header, in, target, maxBytes, image);
            String url = URL_PREFIX + uploadRoot.relativize(target).toString().replace('\\', '/');
            return new StoredMedia(url, total);
        } catch (IOException e) {
            deleteFile(target);
            log.error("商品媒体文件保存失败", e);
            throw BizException.of(ProductErrorCodes.MEDIA_FILE_INVALID, "文件保存失败，请稍后重试");
        } catch (RuntimeException e) {
            deleteFile(target);
            throw e;
        }
    }

    @Override
    public void deleteQuietly(String url) {
        if (url == null || !url.startsWith(URL_PREFIX)) {
            return;
        }
        Path path = uploadRoot.resolve(url.substring(URL_PREFIX.length())).normalize();
        if (path.startsWith(uploadRoot)) {
            deleteFile(path);
        }
    }

    /** 流式写盘，不把整个文件读进内存；实际字节数超过上限时中止（multipart 声明的大小不能完全信任） */
    private static long copyWithLimit(byte[] header, InputStream in, Path target, long maxBytes, boolean image)
            throws IOException {
        long total = header.length;
        try (OutputStream out = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            out.write(header);
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw tooLarge(image);
                }
                out.write(buffer, 0, read);
            }
        }
        return total;
    }

    private static BizException tooLarge(boolean image) {
        return BizException.of(ProductErrorCodes.MEDIA_TOO_LARGE,
                image ? "图片不能超过 5MB" : "视频不能超过 100MB");
    }

    /** 解析后的路径必须仍在上传根目录内（防路径穿越） */
    static Path resolveInside(Path root, Path dir, String filename) {
        Path resolved = dir.resolve(filename).normalize();
        if (!resolved.startsWith(root)) {
            throw BizException.of(ProductErrorCodes.MEDIA_FILE_INVALID, "文件保存路径不合法");
        }
        return resolved;
    }

    /** 按文件头识别格式；识别不了返回空串 */
    static String detectFormat(byte[] h) {
        if (h.length >= 3 && (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF) {
            return "jpeg";
        }
        if (h.length >= 8 && (h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G'
                && h[4] == 0x0D && h[5] == 0x0A && h[6] == 0x1A && h[7] == 0x0A) {
            return "png";
        }
        if (h.length >= 12 && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P') {
            return "webp";
        }
        if (h.length >= 12 && h[4] == 'f' && h[5] == 't' && h[6] == 'y' && h[7] == 'p') {
            return "mp4";
        }
        if (h.length >= 4 && (h[0] & 0xFF) == 0x1A && (h[1] & 0xFF) == 0x45 && (h[2] & 0xFF) == 0xDF
                && (h[3] & 0xFF) == 0xA3) {
            return "webm";
        }
        return "";
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

    private static void deleteFile(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("清理商品媒体文件失败，path={}", path, e);
        }
    }
}
