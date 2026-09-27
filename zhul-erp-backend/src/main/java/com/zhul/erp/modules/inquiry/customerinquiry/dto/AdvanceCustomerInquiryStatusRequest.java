package com.zhul.erp.modules.inquiry.customerinquiry.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdvanceCustomerInquiryStatusRequest {
    /** 目标状态，见 CustomerInquiryStatus */
    @NotNull(message = "目标状态不能为空")
    private Integer targetStatus;
}
