package com.zhul.erp.modules.finance.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 待退款提醒：本人垫付过、有供应商欠款超过 N 天的应付单 */
@Data
public class RefundReminderVO {
    private Long payableId;
    private String payableNo;
    private String payeeName;
    private String sourceNo;
    private String currencyCode;
    private BigDecimal creditAmount;
    private Integer days;
}
