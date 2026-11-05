package com.zhul.erp.modules.crm.followup.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/** 转培育：必须选原因（SOP 卡点）和激活日期 */
@Data
public class NurtureRequest {
    @NotBlank(message = "请选择培育原因")
    private String reason;
    @NotNull(message = "请选择激活日期")
    private LocalDate until;
    @Size(max = 200, message = "说明不能超过200个字符")
    private String note;
}
