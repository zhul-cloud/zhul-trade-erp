package com.zhul.erp.modules.crm.followup.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 设为 / 取消战略客户 */
@Data
public class StrategicRequest {
    @NotNull(message = "请选择设为还是取消战略客户")
    private Boolean strategic;
}
