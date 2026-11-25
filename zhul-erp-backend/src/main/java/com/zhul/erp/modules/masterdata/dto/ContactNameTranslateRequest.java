package com.zhul.erp.modules.masterdata.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 联系人中文名旁「AI 翻译」：把英文姓名翻译成中文 */
@Data
public class ContactNameTranslateRequest {
    @NotBlank(message = "请先填写英文姓名")
    @Size(max = 100, message = "姓名不能超过100个字符")
    private String name;
}
