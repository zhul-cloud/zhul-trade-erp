package com.zhul.erp.modules.inquiry.customerinquiry.service;

import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.AttachmentVO;
import com.zhul.erp.modules.inquiry.customerinquiry.service.impl.AttachmentStorageServiceImpl;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 对应 [[image-excel-attachment-upload]] 的本地磁盘实现：类型/大小校验、
 * URL 与本地路径互相解析（含路径穿越防护）、Excel/CSV 文本抽取。
 */
class AttachmentStorageServiceImplTest {

    private AttachmentStorageServiceImpl service;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        service = new AttachmentStorageServiceImpl();
        ReflectionTestUtils.setField(service, "uploadDir", tempDir.toString());
        TenantContext.setTenantId(1);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void storeImage_withValidPng_savesFileAndReturnsUrl() {
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", new byte[]{1, 2, 3});

        AttachmentVO result = service.storeImage(file);

        assertThat(result.getUrl()).startsWith("/uploads/customer-inquiry/1/").endsWith(".png");
        assertThat(result.getFilename()).isEqualTo("photo.png");
        Path resolved = service.resolveToAbsolutePath(result.getUrl());
        assertThat(Files.exists(resolved)).isTrue();
    }

    @Test
    void storeImage_withDisallowedExtension_throwsBizException() {
        MockMultipartFile file = new MockMultipartFile("file", "malware.exe", "application/octet-stream", new byte[]{1});

        assertThrows(BizException.class, () -> service.storeImage(file));
    }

    @Test
    void storeImage_exceedingSizeLimit_throwsBizException() {
        MockMultipartFile file = new MockMultipartFile("file", "big.png", "image/png", new byte[6 * 1024 * 1024]);

        assertThrows(BizException.class, () -> service.storeImage(file));
    }

    @Test
    void storeExcel_withEmptyFile_throwsBizException() {
        MockMultipartFile file = new MockMultipartFile("file", "empty.xlsx", "application/octet-stream", new byte[0]);

        assertThrows(BizException.class, () -> service.storeExcel(file));
    }

    @Test
    void resolveToAbsolutePath_withPathTraversal_throwsBizException() {
        assertThrows(BizException.class, () -> service.resolveToAbsolutePath("/uploads/../../etc/passwd"));
    }

    @Test
    void resolveToAbsolutePath_withNonUploadsUrl_throwsBizException() {
        assertThrows(BizException.class, () -> service.resolveToAbsolutePath("/other/1/x.png"));
    }

    @Test
    void resolveToAbsolutePath_withRelativeUploadDirConfig_returnsTrueAbsolutePath() {
        // zhul.upload.dir 默认配的就是相对路径（./uploads）。这个方法的返回值会被传给
        // scripts/ai-orchestrator 这个独立进程去读图片文件——如果这里偷懒只做字符串拼接、
        // 不真的转成绝对路径，backend 自己进程内用相对路径能凑合工作（cwd 恰好对），但换一个
        // cwd 不同的进程就会读不到文件（真实复现过："图片文件不存在: uploads/customer-inquiry/..."）。
        ReflectionTestUtils.setField(service, "uploadDir", "./uploads");

        Path result = service.resolveToAbsolutePath("/uploads/customer-inquiry/1000/photo.png");

        assertThat(result.isAbsolute()).isTrue();
    }

    @Test
    void extractExcelText_withRealWorkbook_returnsTabSeparatedContent() throws Exception {
        Path xlsx = tempDir.resolve("orders.xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Sheet1");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("型号");
            header.createCell(1).setCellValue("数量");
            Row data = sheet.createRow(1);
            data.createCell(0).setCellValue("ABC-123");
            data.createCell(1).setCellValue(10);
            try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                workbook.write(bos);
                Files.write(xlsx, bos.toByteArray());
            }
        }

        String text = service.extractExcelText(xlsx);

        assertThat(text).contains("ABC-123").contains("数量");
    }

    @Test
    void extractExcelText_withCsv_returnsRawText() throws Exception {
        Path csv = tempDir.resolve("orders.csv");
        Files.writeString(csv, "型号,数量\nABC-123,10", StandardCharsets.UTF_8);

        String text = service.extractExcelText(csv);

        assertThat(text).isEqualTo("型号,数量\nABC-123,10");
    }

    @Test
    void extractExcelText_withEmptyWorkbook_throwsBizException() throws Exception {
        Path xlsx = tempDir.resolve("blank.xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            workbook.createSheet("Sheet1");
            try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                workbook.write(bos);
                Files.write(xlsx, bos.toByteArray());
            }
        }

        assertThrows(BizException.class, () -> service.extractExcelText(xlsx));
    }
}
