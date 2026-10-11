package com.zhul.erp.modules.product.content.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 上传记录：确认写入的文件；其他公司的资料包记录不显示确认人 */
@Data
public class ContentImportRecordVO {
    private Long id;
    private String fileName;
    /** facts-资料包、marketing-营销包 */
    private String packageType;
    /** alibaba、mic、site、social；资料包为空 */
    private String platform;
    private String lang;
    private String summary;
    /** 确认人；其他公司的记录为空 */
    private String confirmedBy;
    private LocalDateTime confirmedAt;
    /** 是否本公司上传 */
    private boolean mine;
    /** 原文，只在详情里返回 */
    private String rawText;
}
