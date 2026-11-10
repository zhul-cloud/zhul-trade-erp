package com.zhul.erp.modules.upload.dto;

import java.util.List;

/**
 * 准备直传的结果。mode 为 oss 时浏览器直接 PUT 到签名地址，不带登录信息；为 local 时 PUT 到后端分片接口，带登录信息。
 * parts 只给第一批地址，其余用「续签」领取。
 */
public record PrepareUploadVO(String uploadId, String mode, long partSize, int partCount, List<PartUrlVO> parts) {
}
