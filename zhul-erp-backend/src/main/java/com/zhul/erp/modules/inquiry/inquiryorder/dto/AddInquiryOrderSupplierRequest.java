package com.zhul.erp.modules.inquiry.inquiryorder.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 添加报价来源。source_type=1（正式供应商）时 supplierId 必填；
 * source_type=2（电商询价渠道）时 channelPlatform+channelName 必填，
 * 应用层校验，见 specs/inquiry/inquiry-order/spec.md「询盘单关联报价来源区分正式供应商与电商询价渠道」。
 */
@Data
public class AddInquiryOrderSupplierRequest {
    @NotNull(message = "报价来源类型不能为空")
    private Integer sourceType;
    private Long supplierId;
    private Integer channelPlatform;
    private String channelName;
    private String channelLink;
    private LocalDate sentDate;
    private LocalDate replyDeadline;
}
