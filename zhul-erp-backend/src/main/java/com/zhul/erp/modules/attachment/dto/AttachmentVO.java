package com.zhul.erp.modules.attachment.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 附件信息；预览与下载走 GET /api/v1/attachments/{id} */
@Data
public class AttachmentVO {
    private Long id;
    /** 1-图片、2-视频、3-文件（PDF 面单） */
    private Integer kind;
    private String fileName;
    private Long fileSize;
    private String contentType;
    /** 处理状态（0-无需处理、1-转码中、2-已完成、3-失败） */
    private Integer processingStatus;
    private String uploadedByName;
    private LocalDateTime createTime;
}
