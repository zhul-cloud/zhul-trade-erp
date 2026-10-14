package com.zhul.erp.modules.inquiry.customerinquiry.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 追加型号：粘贴内容或附件，选 AI 解析或手动录入 */
@Data
public class AppendRequest {
    @Size(max = 20000, message = "追加内容不能超过 20000 字")
    private String rawContent;
    @Valid
    @Size(max = 10, message = "附件最多 10 个")
    private List<AttachmentRef> attachments;
    /** true 为手动录入（不调用 AI，直接进确认页） */
    private Boolean manual;
}
