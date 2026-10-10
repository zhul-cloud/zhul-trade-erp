package com.zhul.erp.modules.inquiry.sourcing.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 退回询价任务 */
@Data
public class ReturnTaskRequest {
    @NotNull(message = "请选择退回原因")
    private Integer reason;
    @Size(max = 300, message = "说明不能超过 300 字")
    private String note;
}
