package com.zhul.erp.modules.inquiry.customerinquiry.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 确认解析结果 */
@Data
public class ConfirmRequest {
    @Valid
    @NotEmpty(message = "至少需要一个型号")
    @Size(max = 500, message = "一次最多 500 个型号")
    private List<ConfirmRowRequest> rows;
}
