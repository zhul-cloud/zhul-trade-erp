package com.zhul.erp.modules.attachment.support;

import com.zhul.erp.framework.storage.PrivateFileStorage;
import com.zhul.erp.modules.attachment.entity.BizAttachmentDO;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

/** 服务器私有目录：私有存储下的 attachment 模块，只能经鉴权接口读取 */
@Component
@RequiredArgsConstructor
public class LocalAttachmentStore implements AttachmentStore {

    static final String MODULE = "attachment";

    private final PrivateFileStorage storage;

    @Override
    public String storage() {
        return "LOCAL";
    }

    @Override
    public Stored put(int tenantId, MultipartFile file, Set<String> allowedExts, long maxBytes, String typeMessage) {
        PrivateFileStorage.StoredFile f = storage.store(MODULE, tenantId, file, allowedExts, maxBytes, typeMessage);
        return new Stored(f.fileKey(), "", f.fileName(), f.fileSize(), f.contentType());
    }

    @Override
    public Resource open(BizAttachmentDO a) {
        return new FileSystemResource(storage.resolveOwned(MODULE, a.getFileKey(), a.getTenantId()));
    }
}
