package com.zhul.erp.modules.upload.dto;

/** 直传完成后的文件，业务单据保存时提交 fileKey，与普通上传返回的文件一致 */
public record UploadedFileVO(String storage, String fileKey, String fileName, long fileSize, String contentType) {
}
