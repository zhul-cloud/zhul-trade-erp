package com.zhul.erp.modules.quotation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 实时预览：编辑中的内容，不落库 */
@Data
public class PreviewRequest {
    @NotNull(message = "缺少报价单")
    private Long quotationId;
    @NotNull(message = "缺少报价单内容")
    @Valid
    private SaveQuotationRequest content;
}
