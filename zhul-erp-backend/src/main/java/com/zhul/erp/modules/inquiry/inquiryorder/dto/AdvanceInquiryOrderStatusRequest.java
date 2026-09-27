package com.zhul.erp.modules.inquiry.inquiryorder.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdvanceInquiryOrderStatusRequest {
    /** 目标状态，见 InquiryOrderStatus */
    @NotNull(message = "目标状态不能为空")
    private Integer targetStatus;
}
