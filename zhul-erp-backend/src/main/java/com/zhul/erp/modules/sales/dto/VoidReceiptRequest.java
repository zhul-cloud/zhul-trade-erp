package com.zhul.erp.modules.sales.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 作废到账记录 */
@Data
public class VoidReceiptRequest {
    @NotBlank(message = "请填写作废原因")
    @Size(max = 200, message = "作废原因不能超过 200 字")
    private String reason;
}
