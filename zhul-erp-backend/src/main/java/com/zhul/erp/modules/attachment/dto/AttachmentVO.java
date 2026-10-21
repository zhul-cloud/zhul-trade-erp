package com.zhul.erp.modules.attachment.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

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
    /** 标注的型号（供方发货、验收入库、不良品、打包发货的附件有） */
    private List<ModelTagVO> models;
    private String uploadedByName;
    private LocalDateTime createTime;
}
