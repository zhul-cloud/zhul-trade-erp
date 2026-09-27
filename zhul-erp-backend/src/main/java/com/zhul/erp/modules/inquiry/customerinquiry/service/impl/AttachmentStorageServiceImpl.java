package com.zhul.erp.modules.inquiry.customerinquiry.service.impl;

import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.AttachmentVO;
import com.zhul.erp.modules.inquiry.customerinquiry.service.AttachmentStorageService;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

/**
 * 图片/Excel附件的本地磁盘实现，见 {@link AttachmentStorageService} 类注释——
 * 后续迁移到 OSS 时只替换这个类，不改接口、不改调用方。
 */
@Slf4j
@Service
public class AttachmentStorageServiceImpl implements AttachmentStorageService {

    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    private static final String SUB_DIR = "customer-inquiry";

    private static final Map<String, String> ALLOWED_IMAGE_EXT = Map.of(
            "jpg", "jpg", "jpeg", "jpg", "png", "png"
    );
    private static final Map<String, String> ALLOWED_EXCEL_EXT = Map.of(
            "xlsx", "xlsx", "xls", "xls", "csv", "csv"
    );

    @Value("${zhul.upload.dir}")
    private String uploadDir;

    @Override
    public AttachmentVO storeImage(MultipartFile file) {
        return store(file, ALLOWED_IMAGE_EXT, "仅支持 JPG/PNG 格式的图片");
    }

    @Override
    public AttachmentVO storeExcel(MultipartFile file) {
        return store(file, ALLOWED_EXCEL_EXT, "仅支持 XLSX/XLS/CSV 格式的文件");
    }

    private AttachmentVO store(MultipartFile file, Map<String, String> allowedExt, String rejectMessage) {
        if (file == null || file.isEmpty()) {
            throw new BizException("请选择要上传的文件");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BizException("文件大小不能超过5MB");
        }
        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "";
        String ext = extensionOf(originalFilename);
        String normalizedExt = allowedExt.get(ext);
        if (normalizedExt == null) {
            throw new BizException(rejectMessage);
        }

        try {
            int tenantId = currentTenantId();
            Path targetDir = Path.of(uploadDir, SUB_DIR, String.valueOf(tenantId));
            Files.createDirectories(targetDir);
            String storedFilename = UUID.randomUUID().toString().replace("-", "") + "." + normalizedExt;
            Path target = targetDir.resolve(storedFilename);
            file.transferTo(target);
            String url = "/uploads/" + SUB_DIR + "/" + tenantId + "/" + storedFilename;
            return new AttachmentVO(url, originalFilename);
        } catch (IOException e) {
            log.error("附件上传失败", e);
            throw new BizException("附件上传失败，请稍后重试");
        }
    }

    @Override
    public Path resolveToAbsolutePath(String url) {
        if (url == null || !url.startsWith("/uploads/")) {
            throw new BizException("附件地址不合法: " + url);
        }
        // "/uploads/customer-inquiry/1/xxx.png" -> "{uploadDir}/customer-inquiry/1/xxx.png"
        String relative = url.substring("/uploads/".length());
        // zhul.upload.dir 默认是相对路径（./uploads），这里必须转成真正的绝对路径——
        // 这个路径会被传给 scripts/ai-orchestrator 这个独立进程读取图片，它的工作目录
        // 和本进程不一定相同，相对路径对它来说可能根本不存在。
        Path path = Path.of(uploadDir, relative).toAbsolutePath().normalize();
        Path uploadRoot = Path.of(uploadDir).toAbsolutePath().normalize();
        if (!path.startsWith(uploadRoot)) {
            // 防止 url 里带 ../ 之类的路径穿越
            throw new BizException("附件地址不合法: " + url);
        }
        return path;
    }

    @Override
    public String extractExcelText(Path absolutePath) {
        String filename = absolutePath.getFileName().toString();
        if (filename.toLowerCase().endsWith(".csv")) {
            try {
                return Files.readString(absolutePath, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new BizException("读取CSV文件失败: " + e.getMessage());
            }
        }
        try (InputStream in = Files.newInputStream(absolutePath); Workbook workbook = WorkbookFactory.create(in)) {
            DataFormatter formatter = new DataFormatter();
            StringBuilder sb = new StringBuilder();
            for (int sheetIdx = 0; sheetIdx < workbook.getNumberOfSheets(); sheetIdx++) {
                Sheet sheet = workbook.getSheetAt(sheetIdx);
                if (sheet.getPhysicalNumberOfRows() == 0) {
                    continue;
                }
                sb.append("Sheet: ").append(sheet.getSheetName()).append('\n');
                for (Row row : sheet) {
                    boolean rowHasContent = false;
                    StringBuilder line = new StringBuilder();
                    for (Cell cell : row) {
                        String value = formatter.formatCellValue(cell).trim();
                        if (!value.isEmpty()) {
                            rowHasContent = true;
                        }
                        line.append(value).append('\t');
                    }
                    if (rowHasContent) {
                        sb.append(line).append('\n');
                    }
                }
            }
            String result = sb.toString().trim();
            if (result.isEmpty()) {
                throw new BizException("Excel文件内容为空");
            }
            return result;
        } catch (IOException e) {
            throw new BizException("读取Excel文件失败: " + e.getMessage());
        }
    }

    private String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase();
    }

    private int currentTenantId() {
        Integer tenantId = TenantContext.getTenantId();
        return tenantId != null ? tenantId : 0;
    }
}
