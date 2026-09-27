package com.zhul.erp.modules.masterdata.dto;

import java.nio.file.Path;

/** 下载用：附件在私有目录中的绝对路径、原文件名与类型 */
public record SupplierAttachmentFile(Path path, String fileName, String contentType) {
}
