package com.zhul.erp.modules.document.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 保存文字报价模版新版本 */
@Data
public class SaveTextTemplateRequest {
    @NotBlank(message = "模版内容不能为空")
    @Size(max = 2000, message = "模版内容不能超过 2000 字")
    private String content;
    @NotBlank(message = "请填写版本说明")
    @Size(max = 200, message = "版本说明不能超过 200 字")
    private String note;
}
