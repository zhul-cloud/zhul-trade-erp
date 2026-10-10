package com.zhul.erp.modules.inquiry.customerinquiry.dto;

import lombok.Data;

/** 上传或复制后的附件；fileKey 随客户询盘提交才生效 */
@Data
public class UploadedFileVO {
    private String fileKey;
    private String fileName;
    private String contentType;
    private Long fileSize;
}
