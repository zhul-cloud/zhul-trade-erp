package com.zhul.erp.modules.inquiry.sourcing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 粘贴报价的 AI 识别：供应商发来的整段报价 */
@Data
public class PasteAiRequest {
    @NotBlank(message = "请粘贴供应商发来的报价")
    @Size(max = 20000, message = "报价原文不能超过 20000 字，请分段粘贴")
    private String text;
}
