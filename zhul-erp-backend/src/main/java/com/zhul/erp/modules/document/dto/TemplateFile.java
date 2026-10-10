package com.zhul.erp.modules.document.dto;

/** 下载 / 预览导出的文件 */
public record TemplateFile(String fileName, String contentType, byte[] content) {
}
