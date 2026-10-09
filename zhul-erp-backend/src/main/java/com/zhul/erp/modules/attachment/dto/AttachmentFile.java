package com.zhul.erp.modules.attachment.dto;

import org.springframework.core.io.Resource;

/** 下载用：文件内容、原文件名与类型 */
public record AttachmentFile(Resource resource, String fileName, String contentType) {
}
