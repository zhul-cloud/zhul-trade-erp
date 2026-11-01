package com.zhul.erp.modules.aftersales.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 添加跟进记录 */
@Data
public class AfterSalesNoteRequest {
    @NotBlank(message = "请填写跟进内容")
    @Size(max = 1000, message = "跟进内容不能超过 1000 字")
    private String content;
    private List<Long> attachmentIds;
}
