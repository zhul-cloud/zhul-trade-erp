package com.zhul.erp.modules.crm.followup.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 停止开发（第四期 design D8）：原因必填，「其他」必须填说明 */
@Data
public class StopDevelopRequest {
    @NotBlank(message = "请选择停止原因")
    @Size(max = 16, message = "停止原因不正确")
    private String reason;
    @Size(max = 200, message = "说明不能超过200个字")
    private String note;
}
