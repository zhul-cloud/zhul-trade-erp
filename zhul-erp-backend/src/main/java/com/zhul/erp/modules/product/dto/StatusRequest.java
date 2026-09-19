package com.zhul.erp.modules.product.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class StatusRequest {
    /** 状态（0-禁用、1-启用） */
    @NotNull(message = "状态不能为空")
    private Integer status;
}
