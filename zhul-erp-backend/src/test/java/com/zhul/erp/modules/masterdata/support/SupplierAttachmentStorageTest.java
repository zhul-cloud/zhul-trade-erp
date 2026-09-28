package com.zhul.erp.modules.masterdata.support;

import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.storage.PrivateFileStorage;
import com.zhul.erp.modules.masterdata.dto.SupplierAttachmentUploadVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 对应 specs/master-data/supplier-attachment/spec.md「按类型上传附件」「附件访问控制」 */
class SupplierAttachmentStorageTest {

    @TempDir
    Path root;

    private SupplierAttachmentStorage storage() {
        return new SupplierAttachmentStorage(new PrivateFileStorage(root.toString()));
    }

    @Test
    void store_savesUnderTenantFolderWithCleanName() throws Exception {
        SupplierAttachmentUploadVO vo = storage().store(7, new MockMultipartFile("file", "C:\\scan\\营业执照.pdf",
                "application/octet-stream", "%PDF-1.4".getBytes(StandardCharsets.US_ASCII)));

        assertThat(vo.getFileKey()).matches("supplier/7/\\d{6}/[0-9a-f]{32}\\.pdf");
        assertThat(vo.getFileName()).isEqualTo("营业执照.pdf");
        assertThat(vo.getContentType()).isEqualTo("application/pdf");
        assertThat(Files.size(root.resolve(vo.getFileKey()))).isEqualTo(8);
    }

    @Test
    void store_rejectsUnsupportedTypeAndOversize() {
        BizException type = assertThrows(BizException.class, () -> storage().store(7,
                new MockMultipartFile("file", "a.pdf", "application/pdf", "GIF89a..".getBytes(StandardCharsets.US_ASCII))));
        assertThat(type.getMessage()).isEqualTo("只支持 PDF、JPG、PNG");

        byte[] big = new byte[10 * 1024 * 1024 + 1];
        big[0] = '%'; big[1] = 'P'; big[2] = 'D'; big[3] = 'F';
        BizException size = assertThrows(BizException.class,
                () -> storage().store(7, new MockMultipartFile("file", "a.pdf", "application/pdf", big)));
        assertThat(size.getMessage()).isEqualTo("单个文件不能超过 10MB");
    }

    @Test
    void resolveOwned_rejectsOtherTenantForgedAndTraversalKeys() {
        SupplierAttachmentStorage s = storage();
        String key = s.store(7, new MockMultipartFile("file", "a.png", "image/png",
                new byte[]{(byte) 0x89, 'P', 'N', 'G', 1, 2})).getFileKey();

        assertThat(s.resolveOwned(key, 7)).exists();
        assertThrows(BizException.class, () -> s.resolveOwned(key, 8));
        assertThrows(BizException.class, () -> s.resolveOwned("supplier/7/202609/../../../etc/passwd", 7));
        // 其他模块的文件不能当作供应商附件引用
        assertThrows(BizException.class, () -> s.resolveOwned(key.replace("supplier/", "opportunity/"), 7));
        assertThrows(BizException.class, () -> s.resolveOwned("supplier/7/202609/" + "b".repeat(32) + ".png", 7));
        assertThrows(BizException.class, () -> s.resolveOwned(null, 7));
    }
}
