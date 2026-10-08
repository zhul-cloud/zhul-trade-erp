package com.zhul.erp.modules.sales.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 取消认领：到账回到未认领，需要写原因 */
@Data
public class UnclaimReceiptRequest {
    @NotBlank(message = "请填写原因")
    @Size(max = 200, message = "原因不能超过 200 字")
    private String reason;
}
