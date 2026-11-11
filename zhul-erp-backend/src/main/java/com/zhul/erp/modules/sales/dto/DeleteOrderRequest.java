package com.zhul.erp.modules.sales.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 删除销售订单 */
@Data
public class DeleteOrderRequest {
    @NotBlank(message = "请填写删除原因")
    @Size(max = 200, message = "删除原因不能超过 200 字")
    private String reason;
}
