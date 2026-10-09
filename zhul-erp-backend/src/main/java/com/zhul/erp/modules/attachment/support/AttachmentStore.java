package com.zhul.erp.modules.attachment.support;

import com.zhul.erp.modules.attachment.entity.BizAttachmentDO;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

/**
 * 附件的存储方式：本期为服务器私有目录（LOCAL），以后加对象存储（OSS）实现，
 * 新上传的写当前方式，读取时按记录上的 storage 选实现，已有文件不需要迁移。
 */
public interface AttachmentStore {

    /** 存储方式代码，写在附件记录的 storage 字段 */
    String storage();

    /** 校验类型与大小后保存；url 为对象存储的访问链接，本地为空 */
    Stored put(int tenantId, MultipartFile file, Set<String> allowedExts, long maxBytes, String typeMessage);

    Resource open(BizAttachmentDO attachment);

    record Stored(String fileKey, String url, String fileName, long fileSize, String contentType) {
    }
}
