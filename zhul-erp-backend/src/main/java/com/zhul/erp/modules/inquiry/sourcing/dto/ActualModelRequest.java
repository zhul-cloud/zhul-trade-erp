package com.zhul.erp.modules.inquiry.sourcing.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** 采购回填真实型号；空串表示清空 */
@Data
public class ActualModelRequest {
    @Size(max = 128, message = "真实型号不能超过 128 字")
    private String actualModel;
}
