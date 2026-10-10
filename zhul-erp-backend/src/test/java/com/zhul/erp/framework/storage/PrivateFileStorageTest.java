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

class PrivateFileStorageTest {

    @TempDir
    Path root;

    private static byte[] bytes(int... b) {
        byte[] out = new byte[b.length];
        for (int i = 0; i < b.length; i++) {
            out[i] = (byte) b[i];
        }
        return out;
    }

    @Test
    void detect_byFileHeader_excelNeedsMatchingExtension() {
        assertThat(PrivateFileStorage.detect("%PDF-1.7".getBytes(StandardCharsets.US_ASCII), "a.bin")).isEqualTo("pdf");
        assertThat(PrivateFileStorage.detect(bytes(0x89, 'P', 'N', 'G', 0), "a.jpg")).isEqualTo("png");
        assertThat(PrivateFileStorage.detect(bytes(0xFF, 0xD8, 0xFF, 0), "a")).isEqualTo("jpg");
        assertThat(PrivateFileStorage.detect(bytes('P', 'K', 3, 4, 0), "清单.XLSX")).isEqualTo("xlsx");
        assertThat(PrivateFileStorage.detect(bytes('P', 'K', 3, 4, 0), "合同.docx")).isNull();
        assertThat(PrivateFileStorage.detect(bytes(0xD0, 0xCF, 0x11, 0xE0), "旧表.xls")).isEqualTo("xls");
        assertThat(PrivateFileStorage.detect("model,qty\n6ES7,50".getBytes(StandardCharsets.UTF_8), "bom.csv")).isEqualTo("csv");
        assertThat(PrivateFileStorage.detect(bytes('a', 0, 'b'), "bin.csv")).isNull();
        assertThat(PrivateFileStorage.detect(bytes('I', 'I', 42, 0), "scan.tiff")).isNull();
    }

    @Test
    void store_rejectsTypeNotAllowedForModule_andKeyBoundToModuleAndTenant() {
        PrivateFileStorage s = new PrivateFileStorage(root.toString());
        MockMultipartFile pdf = new MockMultipartFile("file", "a.pdf", "application/pdf", "%PDF-1".getBytes(StandardCharsets.US_ASCII));
        BizException e = assertThrows(BizException.class,
                () -> s.store("opportunity", 3, pdf, Set.of("jpg", "png"), 1024, "只支持图片"));
        assertThat(e.getMessage()).isEqualTo("只支持图片");

        PrivateFileStorage.StoredFile f = s.store("opportunity", 3,
                new MockMultipartFile("file", "a.csv", "text/csv", "a,b".getBytes(StandardCharsets.UTF_8)),
                Set.of("csv"), 1024, "x");
        assertThat(f.fileKey()).matches("opportunity/3/\\d{6}/[0-9a-f]{32}\\.csv");
        assertThat(f.contentType()).isEqualTo("text/csv");
        assertThat(s.resolveOwned("opportunity", f.fileKey(), 3)).exists();
        assertThrows(BizException.class, () -> s.resolveOwned("supplier", f.fileKey(), 3));
        assertThrows(BizException.class, () -> s.resolveOwned("opportunity", f.fileKey(), 4));
    }

    @Test
    void store_rejectsOversize() {
        PrivateFileStorage s = new PrivateFileStorage(root.toString());
        BizException e = assertThrows(BizException.class, () -> s.store("opportunity", 3,
                new MockMultipartFile("file", "a.png", "image/png", new byte[2 * 1024 * 1024 + 1]),
                Set.of("png"), 2L * 1024 * 1024, "x"));
        assertThat(e.getMessage()).isEqualTo("单个文件不能超过 2MB");
    }
}
