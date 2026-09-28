package com.zhul.erp.modules.crm.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** 带 id 表示保留已有附件；不带 id 时 fileKey 为上传接口返回的值 */
@Data
public class OpportunityAttachmentRequest {

    private Long id;

    @Size(max = 200, message = "文件名不能超过200个字符")
    private String fileName;

    @Size(max = 200, message = "附件标识不正确")
    private String fileKey;
}
