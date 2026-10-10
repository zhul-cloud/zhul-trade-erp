package com.zhul.erp.modules.purchase.dto;

import java.nio.file.Path;

/** 下载用：文件路径、原文件名与类型 */
public record PurchaseAttachmentFile(Path path, String fileName, String contentType) {
}
