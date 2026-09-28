package com.zhul.erp.modules.crm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 在进行中阶段之间前进或回退 */
@Data
public class ChangeStageRequest {

    @NotBlank(message = "请选择阶段")
    private String toStage;

    @Size(max = 500, message = "说明不能超过500个字符")
    private String note;
}
