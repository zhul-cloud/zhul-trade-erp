package com.zhul.erp.modules.crm.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 附件出参，不含存储路径 */
@Data
public class OpportunityAttachmentVO {

    private Long id;
    private String fileName;
    private Long fileSize;
    private String contentType;
    private String createBy;
    private LocalDateTime createTime;
}
