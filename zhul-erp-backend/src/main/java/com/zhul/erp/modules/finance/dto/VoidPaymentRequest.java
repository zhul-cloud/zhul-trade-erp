package com.zhul.erp.modules.finance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class VoidPaymentRequest {
    @NotBlank(message = "请填写作废原因")
    @Size(max = 255, message = "原因不能超过 255 字")
    private String reason;
}
