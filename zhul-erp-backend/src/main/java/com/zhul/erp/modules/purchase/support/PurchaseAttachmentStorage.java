package com.zhul.erp.modules.purchase.support;

import com.zhul.erp.framework.storage.PrivateFileStorage;
import com.zhul.erp.modules.purchase.constants.PurchaseConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.Set;

/** 采购单合同附件：私有目录下的 purchase 模块，只收 PDF、JPG、PNG（按文件内容校验） */
@Component
@RequiredArgsConstructor
public class PurchaseAttachmentStorage {

    private static final String MODULE = "purchase";
    private static final Set<String> ALLOWED = Set.of("pdf", "jpg", "png");

    private final PrivateFileStorage storage;

    public PrivateFileStorage.StoredFile store(int tenantId, MultipartFile file) {
        return storage.store(MODULE, tenantId, file, ALLOWED, PurchaseConstants.ATTACHMENT_MAX_BYTES, "只支持 PDF、JPG、PNG");
    }

    public Path resolveOwned(String fileKey, int tenantId) {
        return storage.resolveOwned(MODULE, fileKey, tenantId);
    }
}
