package com.zhul.erp.modules.sales.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** 取消销售订单 */
@Data
public class CancelOrderRequest {
    @Size(max = 200, message = "取消原因不能超过 200 字")
    private String reason;
}
