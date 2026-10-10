package com.zhul.erp.modules.warehouse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 填写原因的操作：作废、冲销、跳过 */
@Data
public class ReasonRequest {
    @NotBlank(message = "请填写原因")
    @Size(max = 200, message = "原因不能超过200个字")
    private String reason;
}
