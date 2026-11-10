package com.zhul.erp.modules.upload.dto;

/** 某个分片的上传地址：OSS 时是签名地址，本地存储时是后端的分片接口 */
public record PartUrlVO(int n, String url) {
}
