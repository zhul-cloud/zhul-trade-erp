package com.zhul.erp.modules.crm.followup.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 提交 AI 整理：粘贴的聊天或邮件原文（超过 20,000 字只取最后 20,000 字） */
@Data
public class ExtractRequest {
    @NotNull(message = "请选择客户")
    private Long customerId;
    private Long itemId;
    @NotBlank(message = "请粘贴聊天或邮件内容")
    private String rawContent;
}
