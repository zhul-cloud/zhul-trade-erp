package com.zhul.erp.modules.inquiry.customerinquiry.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 提交客户询盘时引用的已上传附件 */
@Data
public class AttachmentRef {
    @NotBlank(message = "附件标识不能为空")
    private String fileKey;
    private String fileName;
}
