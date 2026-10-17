package com.zhul.erp.framework.storage;

import com.zhul.erp.common.exception.BizException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Locale;
import java.util.Set;

/**
 * 上传文件的公共校验：非空、大小上限（按原文件）、按文件头识别类型并检查白名单。
 * 文件类型按文件头识别，不看扩展名；xlsx、xls 还要求扩展名一致（ZIP、OLE 容器也可能是 docx、doc），csv 没有文件头，按扩展名且内容为文本判断。
 */
public final class UploadRules {

    public static final Set<String> IMAGE_EXTS = Set.of("jpg", "png", "webp");
    public static final Set<String> VIDEO_EXTS = Set.of("mp4", "mov", "webm");
    private static final int HEADER_BYTES = 512;
    private static final int NAME_MAX = 200;

    private UploadRules() {
    }

    /**
     * 校验并返回识别出的扩展名。
     *
     * @param typeMessage 类型不符时的提示，如「只支持 PDF、JPG、PNG」
     */
    public static String check(MultipartFile file, Set<String> allowedExts, long maxBytes, String typeMessage) {
        if (file == null || file.isEmpty()) {
            throw new BizException("请选择要上传的文件");
        }
        if (file.getSize() > maxBytes) {
            throw new BizException("单个文件不能超过 " + (maxBytes / 1024 / 1024) + "MB");
        }
        String ext = detect(head(file), file.getOriginalFilename());
        if (ext == null || !allowedExts.contains(ext)) {
            throw new BizException(typeMessage);
        }
        return ext;
    }

    /** 文件开头的若干字节，用于识别类型 */
    public static byte[] head(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return in.readNBytes(HEADER_BYTES);
        } catch (IOException e) {
            throw new UncheckedIOException("读取上传文件失败", e);
        }
    }

    /**
     * 识别文件类型：PDF「%PDF」、PNG「89 50 4E 47」、JPG「FF D8 FF」、GIF「GIF8」、WEBP「RIFF….WEBP」、
     * MP4/MOV（偏移 4 处为 ftyp）、WEBM（EBML 头 1A 45 DF A3）、xls「D0 CF 11 E0」、xlsx「PK 03 04」、csv。
     */
    public static String detect(byte[] head, String originalName) {
        String name = originalName == null ? "" : originalName.toLowerCase(Locale.ROOT);
        if (startsWith(head, '%', 'P', 'D', 'F')) {
            return "pdf";
        }
        if (startsWith(head, 0x89, 'P', 'N', 'G')) {
            return "png";
        }
        if (startsWith(head, 0xFF, 0xD8, 0xFF)) {
            return "jpg";
        }
        if (startsWith(head, 'G', 'I', 'F', '8')) {
            return "gif";
        }
        if (startsWith(head, 'R', 'I', 'F', 'F') && head.length >= 12
                && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P') {
            return "webp";
        }
        // MP4 / MOV：偏移 4 处为「ftyp」，主品牌「qt  」为 MOV，其余按 MP4
        if (head.length >= 12 && head[4] == 'f' && head[5] == 't' && head[6] == 'y' && head[7] == 'p') {
            return head[8] == 'q' && head[9] == 't' ? "mov" : "mp4";
        }
        if (startsWith(head, 0x1A, 0x45, 0xDF, 0xA3)) {
            return "webm";
        }
        if (startsWith(head, 0xD0, 0xCF, 0x11, 0xE0) && name.endsWith(".xls")) {
            return "xls";
        }
        if (startsWith(head, 'P', 'K', 0x03, 0x04) && name.endsWith(".xlsx")) {
            return "xlsx";
        }
        if (name.endsWith(".csv") && head.length > 0 && isText(head)) {
            return "csv";
        }
        return null;
    }

    public static boolean isImage(String ext) {
        return IMAGE_EXTS.contains(ext);
    }

    public static boolean isVideo(String ext) {
        return VIDEO_EXTS.contains(ext);
    }

    /** 按扩展名给出文件类型 */
    public static String contentTypeOf(String fileKeyOrExt) {
        return switch (fileKeyOrExt.substring(fileKeyOrExt.lastIndexOf('.') + 1)) {
            case "pdf" -> "application/pdf";
            case "png" -> "image/png";
            case "jpg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "svg" -> "image/svg+xml";
            case "mp4" -> "video/mp4";
            case "mov" -> "video/quicktime";
            case "webm" -> "video/webm";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "xls" -> "application/vnd.ms-excel";
            case "csv" -> "text/csv";
            default -> "application/octet-stream";
        };
    }

    public static String extOf(String fileKey) {
        return fileKey.substring(fileKey.lastIndexOf('.') + 1);
    }

    /** 只保留文件名本身（去掉路径），超长截断；空时用「附件.扩展名」 */
    public static String cleanFileName(String original, String ext) {
        String name = original == null ? "" : original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).trim();
        if (name.isEmpty()) {
            name = "附件." + ext;
        }
        return name.length() > NAME_MAX ? name.substring(name.length() - NAME_MAX) : name;
    }

    /** 压缩改变了格式（如 WEBP 转为 JPG）时，下载文件名的扩展名跟着改 */
    public static String withExt(String fileName, String ext) {
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        return base + "." + ext;
    }

    private static boolean startsWith(byte[] head, int... magic) {
        if (head.length < magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if ((head[i] & 0xFF) != magic[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean isText(byte[] head) {
        for (byte b : head) {
            if (b == 0) {
                return false;
            }
        }
        return true;
    }
}
