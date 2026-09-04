package com.zhul.erp.modules.inquiry.inquiryorder.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AssignPurchaserRequest {
    @NotNull(message = "采购员不能为空")
    private Long assigneeId;
}
