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
    /** 除待询价型号外，要一起交出的无货或有价型号 */
    private java.util.List<Long> itemIds;
}
