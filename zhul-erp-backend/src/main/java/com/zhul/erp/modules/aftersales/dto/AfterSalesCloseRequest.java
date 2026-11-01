package com.zhul.erp.modules.aftersales.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 结案 */
@Data
public class AfterSalesCloseRequest {
    @NotBlank(message = "请选择最终原因与责任")
    private String reasonCode;
    @NotBlank(message = "请填写处理结果")
    @Size(max = 1000, message = "处理结果不能超过 1000 字")
    private String result;
}
