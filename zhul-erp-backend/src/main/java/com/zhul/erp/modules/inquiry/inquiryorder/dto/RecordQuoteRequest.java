package com.zhul.erp.modules.inquiry.inquiryorder.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 录入报价（任务5.6）。汇率未维护且币种非本位币时，quotePriceCny 由服务端留空，
 * 不由前端传入计算结果，避免前后端精度算法不一致。
 */
@Data
public class RecordQuoteRequest {
    @NotNull(message = "询盘单明细不能为空")
    private Long inquiryOrderItemId;
    @NotNull(message = "报价来源不能为空")
    private Long inquiryOrderSupplierId;
    private BigDecimal quotePriceOriginal;
    private String currencyCode;
    private BigDecimal exchangeRate;
    private String supplierDelivery;
    @NotNull(message = "报价状态不能为空")
    private Integer quoteStatus;
}
