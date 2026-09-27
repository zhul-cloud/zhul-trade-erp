package com.zhul.erp.modules.masterdata.support;

import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.masterdata.constants.SupplierConstants;
import com.zhul.erp.modules.masterdata.dto.SupplierAttachmentUploadVO;
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
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 供应商附件的私有存储：放在 {@code zhul.upload.private-dir} 下，不在 /uploads 静态资源映射内，
 * 只能经需要权限的下载接口读取。以后迁 OSS 只需替换本类。
 * <p>
 * 路径形如 {@code supplier/{tenantId}/{yyyyMM}/{uuid}.{pdf|jpg|png}}；保存供应商时提交的 fileKey 必须匹配该格式且租户段等于当前租户。
 */
@Component
public class SupplierAttachmentStorage {

    private static final Pattern KEY_PATTERN = Pattern.compile("^supplier/(\\d+)/\\d{6}/[0-9a-f]{32}\\.(pdf|jpg|png)$");
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM");
    private static final int HEADER_BYTES = 8;

    private final Path root;

    public SupplierAttachmentStorage(@Value("${zhul.upload.private-dir:./private-uploads}") String privateDir) {
        this.root = Paths.get(privateDir).toAbsolutePath().normalize();
    }

    /** 校验并保存上传文件：按文件头识别 PDF / JPG / PNG，不看扩展名 */
    public SupplierAttachmentUploadVO store(int tenantId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException("请选择要上传的文件");
        }
        if (file.getSize() > SupplierConstants.ATTACHMENT_MAX_BYTES) {
            throw new BizException("单个文件不能超过 10MB");
        }
        String ext;
        try (InputStream in = file.getInputStream()) {
            ext = detect(in.readNBytes(HEADER_BYTES));
        } catch (IOException e) {
            throw new UncheckedIOException("读取上传文件失败", e);
        }
        if (ext == null) {
            throw new BizException("只支持 PDF、JPG、PNG");
        }
        String key = "supplier/" + tenantId + "/" + LocalDate.now().format(MONTH) + "/"
                + UUID.randomUUID().toString().replace("-", "") + "." + ext;
        Path target = root.resolve(key);
        try (InputStream in = file.getInputStream()) {
            Files.createDirectories(target.getParent());
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("保存上传文件失败", e);
        }
        SupplierAttachmentUploadVO vo = new SupplierAttachmentUploadVO();
        vo.setFileKey(key);
        vo.setFileName(cleanFileName(file.getOriginalFilename(), ext));
        vo.setFileSize(file.getSize());
        vo.setContentType(contentType(ext));
        return vo;
    }

    /** fileKey 属于该租户且文件存在时返回其绝对路径，否则抛业务异常（防止伪造路径或引用其他租户的文件） */
    public Path resolveOwned(String fileKey, int tenantId) {
        Matcher m = fileKey == null ? null : KEY_PATTERN.matcher(fileKey);
        if (m == null || !m.matches() || Integer.parseInt(m.group(1)) != tenantId) {
            throw new BizException("附件不存在，请重新上传");
        }
        Path path = root.resolve(fileKey).normalize();
        if (!path.startsWith(root) || !Files.isRegularFile(path)) {
            throw new BizException("附件不存在，请重新上传");
        }
        return path;
    }

    public static String contentTypeOf(String fileKey) {
        return contentType(fileKey.substring(fileKey.lastIndexOf('.') + 1));
    }

    /** 文件头：PDF「%PDF」、PNG「89 50 4E 47」、JPG「FF D8 FF」 */
    static String detect(byte[] head) {
        if (head.length >= 4 && head[0] == '%' && head[1] == 'P' && head[2] == 'D' && head[3] == 'F') {
            return "pdf";
        }
        if (head.length >= 4 && (head[0] & 0xFF) == 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G') {
            return "png";
        }
        if (head.length >= 3 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        return null;
    }

    private static String contentType(String ext) {
        return switch (ext) {
            case "pdf" -> "application/pdf";
            case "png" -> "image/png";
            default -> "image/jpeg";
        };
    }

    /** 只保留文件名本身（去掉路径），超长截断；空时用「附件.扩展名」 */
    public static String cleanFileName(String original, String ext) {
        String name = original == null ? "" : original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).trim();
        if (name.isEmpty()) {
            name = "附件." + ext;
        }
        return name.length() > 200 ? name.substring(name.length() - 200) : name;
    }
}
