package com.zhul.erp.modules.inquiry.customerinquiry.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 确认拆单时提交的单条明细——只携带业务员可编辑的字段。 */
@Data
public class ConfirmSplitItemRequest {
    @NotBlank(message = "原始型号不能为空")
    private String originalModel;
    @NotBlank(message = "确认型号不能为空")
    private String confirmedModel;
    /** 置信度，见 ConfidenceLevel；原样回传预览页展示的值，本接口不做自动转换 */
    private Integer confidence;
    private String correctionNote;
    private String description;
    private Integer quantity;
    private String unit;
    private String deliveryRequirement;
    private String remark;
}
