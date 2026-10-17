package com.zhul.erp.framework.storage;

import com.zhul.erp.common.exception.BizException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 公共上传校验（文件头识别、白名单、大小）与存储服务的上传、校验文件键、复制 */
class UploadRulesAndServiceTest {

    @TempDir
    Path root;

    private static byte[] bytes(int... b) {
        byte[] out = new byte[b.length];
        for (int i = 0; i < b.length; i++) {
            out[i] = (byte) b[i];
        }
        return out;
    }

    private FileStorageService service() {
        StorageProperties props = new StorageProperties();
        LocalFileStorage local = new LocalFileStorage(root.resolve("priv").toString(), root.resolve("pub").toString(), "s");
        return new FileStorageService(new FileStorages(props, local, null), props);
    }

    @Test
    void detect_byFileHeader_excelNeedsMatchingExtension() {
        assertThat(UploadRules.detect("%PDF-1.7".getBytes(StandardCharsets.US_ASCII), "a.bin")).isEqualTo("pdf");
        assertThat(UploadRules.detect(bytes(0x89, 'P', 'N', 'G', 0), "a.jpg")).isEqualTo("png");
        assertThat(UploadRules.detect(bytes(0xFF, 0xD8, 0xFF, 0), "a")).isEqualTo("jpg");
        assertThat(UploadRules.detect(bytes('P', 'K', 3, 4, 0), "清单.XLSX")).isEqualTo("xlsx");
        assertThat(UploadRules.detect(bytes('P', 'K', 3, 4, 0), "合同.docx")).isNull();
        assertThat(UploadRules.detect(bytes(0xD0, 0xCF, 0x11, 0xE0), "旧表.xls")).isEqualTo("xls");
        assertThat(UploadRules.detect("model,qty\n6ES7,50".getBytes(StandardCharsets.UTF_8), "bom.csv")).isEqualTo("csv");
        assertThat(UploadRules.detect(bytes('a', 0, 'b'), "bin.csv")).isNull();
        assertThat(UploadRules.detect(bytes('I', 'I', 42, 0), "scan.tiff")).isNull();
        assertThat(UploadRules.detect(bytes(0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'q', 't', ' ', ' '), "a.mov")).isEqualTo("mov");
        assertThat(UploadRules.detect(bytes(0x1A, 0x45, 0xDF, 0xA3), "a.webm")).isEqualTo("webm");
    }

    @Test
    void check_typeNotAllowed_oversize_empty() {
        MockMultipartFile pdf = new MockMultipartFile("file", "a.pdf", "application/pdf", "%PDF-1".getBytes(StandardCharsets.US_ASCII));
        assertThat(assertThrows(BizException.class, () -> UploadRules.check(pdf, Set.of("jpg", "png"), 1024, "只支持图片")).getMessage())
                .isEqualTo("只支持图片");
        MockMultipartFile big = new MockMultipartFile("file", "a.png", "image/png", new byte[2 * 1024 * 1024 + 1]);
        assertThat(assertThrows(BizException.class, () -> UploadRules.check(big, Set.of("png"), 2L * 1024 * 1024, "x")).getMessage())
                .isEqualTo("单个文件不能超过 2MB");
        MockMultipartFile empty = new MockMultipartFile("file", "a.png", "image/png", new byte[0]);
        assertThat(assertThrows(BizException.class, () -> UploadRules.check(empty, Set.of("png"), 10, "x")).getMessage())
                .isEqualTo("请选择要上传的文件");
    }

    @Test
    void store_locate_copy_keyBoundToAreaAndTenant() {
        FileStorageService s = service();
        FileStorageService.StoredFile f = s.store(StorageArea.OPPORTUNITY, 3,
                new MockMultipartFile("file", "dir/a.csv", "text/csv", "a,b".getBytes(StandardCharsets.UTF_8)), Set.of("csv"), 1024, "x");
        assertThat(f.storage()).isEqualTo(FileStorage.LOCAL);
        assertThat(f.fileKey()).matches("tenant/3/opportunity/\\d{6}/[0-9a-f]{32}\\.csv");
        assertThat(f.fileName()).isEqualTo("a.csv");
        assertThat(f.contentType()).isEqualTo("text/csv");
        assertThat(s.locate(StorageArea.OPPORTUNITY, f.fileKey(), 3)).isEqualTo(FileStorage.LOCAL);
        assertThrows(BizException.class, () -> s.locate(StorageArea.SUPPLIER, f.fileKey(), 3));
        assertThrows(BizException.class, () -> s.locate(StorageArea.OPPORTUNITY, f.fileKey(), 4));
        assertThrows(BizException.class, () -> s.locate(StorageArea.OPPORTUNITY, f.fileKey().replace(".csv", ".pdf"), 3));

        FileStorageService.StoredFile c = s.copy(f.storage(), f.fileKey(), StorageArea.INQUIRY, 3, "bom.csv");
        assertThat(c.fileKey()).startsWith("tenant/3/inquiry/");
        assertThat(new String(s.readAll(c.storage(), c.fileKey()), StandardCharsets.UTF_8)).isEqualTo("a,b");
    }

    @Test
    void store_webpBecomesJpg_fileNameFollows_andLyingSizeRejected() {
        FileStorageService s = service();
        MockMultipartFile lying = new MockMultipartFile("file", "a.pdf", "application/pdf", "%PDF-1.7 0123456789".getBytes(StandardCharsets.US_ASCII)) {
            @Override
            public long getSize() {
                return 5;
            }
        };
        assertThat(assertThrows(BizException.class, () -> s.store(StorageArea.FINANCE, 1, lying, Set.of("pdf"), 1024, "x")).getMessage())
                .contains("大小");
        assertThat(UploadRules.withExt("回单.webp", "jpg")).isEqualTo("回单.jpg");
        assertThat(UploadRules.withExt("无扩展名", "jpg")).isEqualTo("无扩展名.jpg");
    }
}
