package com.zhul.erp.modules.purchase.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 撤销已开票 */
@Data
public class RevokeInvoiceRequest {
    @NotBlank(message = "请填写撤销原因")
    @Size(max = 200, message = "撤销原因不能超过 200 个字符")
    private String reason;
}
