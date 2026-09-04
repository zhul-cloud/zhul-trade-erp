package com.zhul.erp.modules.inquiry.customerinquiry.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 提交客户询盘。仅创建记录（status=待解析），不自动触发AI解析，见
 * specs/inquiry/customer-inquiry/spec.md「提交客户询盘不自动触发解析」。
 */
@Data
public class SubmitCustomerInquiryRequest {
    @NotNull(message = "客户不能为空")
    private Long customerId;
    @NotNull(message = "询盘来源不能为空")
    private Integer source;
    private String rawContent;
    private String rawAttachmentUrl;
    private LocalDate inquiryDate;
    private LocalDate expectedReplyDate;
    private String remark;
}
