package com.zhul.erp.modules.inquiry.customerinquiry.dto;

import lombok.Data;

/** 询盘单附件 */
@Data
public class AttachmentVO {
    private Long id;
    private String fileName;
    private String contentType;
    private Long fileSize;
}
