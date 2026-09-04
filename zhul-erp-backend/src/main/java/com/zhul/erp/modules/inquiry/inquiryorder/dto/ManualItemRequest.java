package com.zhul.erp.modules.inquiry.inquiryorder.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 手动新建询盘单时的明细：原始型号=确认型号，无置信度、无AI纠正字段（PRD 6.6）。 */
@Data
public class ManualItemRequest {
    @NotBlank(message = "型号不能为空")
    private String model;
    private Integer quantity;
    private String unit;
    private String remark;
}
