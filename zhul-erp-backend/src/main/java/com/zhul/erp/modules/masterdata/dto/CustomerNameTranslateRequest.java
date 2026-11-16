package com.zhul.erp.modules.masterdata.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 客户编辑页「AI 翻译」：把英文名称翻译成中文 */
@Data
public class CustomerNameTranslateRequest {
    @NotBlank(message = "请先填写客户名称（英文）")
    @Size(max = 200, message = "客户名称不能超过200个字符")
    private String name;
}
