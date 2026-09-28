package com.zhul.erp.framework.storage;

import com.zhul.erp.common.exception.BizException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 私有文件存储：放在 {@code zhul.upload.private-dir} 下，不在 /uploads 静态资源映射内，只能经需要权限的下载接口读取。
 * 以后迁 OSS 只需替换本类。
 * <p>
 * 路径形如 {@code {module}/{tenantId}/{yyyyMM}/{uuid}.{ext}}；业务保存时提交的 fileKey 必须匹配该格式、
 * 模块与租户段等于当前模块与租户，防止伪造路径或引用其他租户的文件。
 * 文件类型按文件头识别（PDF、PNG、JPG、xlsx、xls），不看扩展名；csv 没有文件头，按扩展名且内容为文本判断。
 */
@Component
public class PrivateFileStorage {

    private static final Pattern KEY_PATTERN =
            Pattern.compile("^([a-z]+)/(\\d+)/\\d{6}/[0-9a-f]{32}\\.(pdf|jpg|png|xlsx|xls|csv)$");
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM");
    private static final int HEADER_BYTES = 512;
    private static final int NAME_MAX = 200;

    private final Path root;

    public PrivateFileStorage(@Value("${zhul.upload.private-dir:./private-uploads}") String privateDir) {
        this.root = Paths.get(privateDir).toAbsolutePath().normalize();
    }

    /** 已保存文件的信息：fileKey 随业务单据提交后才算生效 */
    public record StoredFile(String fileKey, String fileName, long fileSize, String contentType) {
    }

    /**
     * 校验并保存上传文件。
     *
     * @param allowedExts  允许的类型（pdf、jpg、png、xlsx、xls、csv 中的若干个）
     * @param typeMessage  类型不符时的提示，如「只支持 PDF、JPG、PNG」
     */
    public StoredFile store(String module, int tenantId, MultipartFile file, Set<String> allowedExts,
                            long maxBytes, String typeMessage) {
        if (file == null || file.isEmpty()) {
            throw new BizException("请选择要上传的文件");
        }
        if (file.getSize() > maxBytes) {
            throw new BizException("单个文件不能超过 " + (maxBytes / 1024 / 1024) + "MB");
        }
        String ext;
        try (InputStream in = file.getInputStream()) {
            ext = detect(in.readNBytes(HEADER_BYTES), file.getOriginalFilename());
        } catch (IOException e) {
            throw new UncheckedIOException("读取上传文件失败", e);
        }
        if (ext == null || !allowedExts.contains(ext)) {
            throw new BizException(typeMessage);
        }
        String key = module + "/" + tenantId + "/" + LocalDate.now().format(MONTH) + "/"
                + UUID.randomUUID().toString().replace("-", "") + "." + ext;
        Path target = root.resolve(key);
        try (InputStream in = file.getInputStream()) {
            Files.createDirectories(target.getParent());
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("保存上传文件失败", e);
        }
        return new StoredFile(key, cleanFileName(file.getOriginalFilename(), ext), file.getSize(), contentTypeOf(key));
    }

    /** fileKey 属于该模块、该租户且文件存在时返回其绝对路径，否则抛业务异常 */
    public Path resolveOwned(String module, String fileKey, int tenantId) {
        Matcher m = fileKey == null ? null : KEY_PATTERN.matcher(fileKey);
        if (m == null || !m.matches() || !m.group(1).equals(module) || Integer.parseInt(m.group(2)) != tenantId) {
            throw new BizException("附件不存在，请重新上传");
        }
        Path path = root.resolve(fileKey).normalize();
        if (!path.startsWith(root) || !Files.isRegularFile(path)) {
            throw new BizException("附件不存在，请重新上传");
        }
        return path;
    }

    /** 按 fileKey 的扩展名给出文件类型 */
    public static String contentTypeOf(String fileKey) {
        return switch (fileKey.substring(fileKey.lastIndexOf('.') + 1)) {
            case "pdf" -> "application/pdf";
            case "png" -> "image/png";
            case "jpg" -> "image/jpeg";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "xls" -> "application/vnd.ms-excel";
            case "csv" -> "text/csv";
            default -> "application/octet-stream";
        };
    }

    public static String extOf(String fileKey) {
        return fileKey.substring(fileKey.lastIndexOf('.') + 1);
    }

    /**
     * 识别文件类型：PDF「%PDF」、PNG「89 50 4E 47」、JPG「FF D8 FF」、xls「D0 CF 11 E0」（OLE）、
     * xlsx「PK 03 04」且扩展名为 xlsx（ZIP 容器也可能是 docx 等，靠扩展名区分）、csv 扩展名为 csv 且开头没有 NUL 字节。
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

    /** 只保留文件名本身（去掉路径），超长截断；空时用「附件.扩展名」 */
    public static String cleanFileName(String original, String ext) {
        String name = original == null ? "" : original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).trim();
        if (name.isEmpty()) {
            name = "附件." + ext;
        }
        return name.length() > NAME_MAX ? name.substring(name.length() - NAME_MAX) : name;
    }
}
