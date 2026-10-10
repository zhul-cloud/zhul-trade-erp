package com.zhul.erp.modules.inquiry.sourcing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 粘贴报价：店家回复的原文 */
@Data
public class PastePreviewRequest {
    @NotBlank(message = "请粘贴店家回复的报价")
    @Size(max = 10000, message = "报价原文不能超过 10000 字")
    private String text;
}
