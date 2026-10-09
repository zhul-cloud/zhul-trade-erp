package com.zhul.erp.modules.purchase.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CancelPurchaseOrderRequest {
    @NotBlank(message = "请填写取消原因")
    @Size(max = 200, message = "取消原因不能超过 200 个字符")
    private String reason;
}
