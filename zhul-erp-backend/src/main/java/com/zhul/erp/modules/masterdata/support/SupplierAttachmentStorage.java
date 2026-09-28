package com.zhul.erp.modules.masterdata.support;

import com.zhul.erp.framework.storage.PrivateFileStorage;
import com.zhul.erp.modules.masterdata.constants.SupplierConstants;
import com.zhul.erp.modules.masterdata.dto.SupplierAttachmentUploadVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.Set;

/** 供应商附件存储：私有目录下的 supplier 模块，只收 PDF、JPG、PNG */
@Component
@RequiredArgsConstructor
public class SupplierAttachmentStorage {

    private static final String MODULE = "supplier";
    private static final Set<String> ALLOWED = Set.of("pdf", "jpg", "png");

    private final PrivateFileStorage storage;

    public SupplierAttachmentUploadVO store(int tenantId, MultipartFile file) {
        PrivateFileStorage.StoredFile stored = storage.store(MODULE, tenantId, file, ALLOWED,
                SupplierConstants.ATTACHMENT_MAX_BYTES, "只支持 PDF、JPG、PNG");
        SupplierAttachmentUploadVO vo = new SupplierAttachmentUploadVO();
        vo.setFileKey(stored.fileKey());
        vo.setFileName(stored.fileName());
        vo.setFileSize(stored.fileSize());
        vo.setContentType(stored.contentType());
        return vo;
    }

    /** fileKey 属于该租户的供应商附件且文件存在时返回其绝对路径，否则抛业务异常 */
    public Path resolveOwned(String fileKey, int tenantId) {
        return storage.resolveOwned(MODULE, fileKey, tenantId);
    }

    public static String contentTypeOf(String fileKey) {
        return PrivateFileStorage.contentTypeOf(fileKey);
    }

    public static String cleanFileName(String original, String ext) {
        return PrivateFileStorage.cleanFileName(original, ext);
    }
}
