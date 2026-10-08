package com.zhul.erp.modules.sales.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 收款管理里的一笔到账（未认领到账、可认领列表、收款记录共用） */
@Data
public class ReceiptRowVO {
    private Long id;
    private Long piId;
    private String piNo;
    private String customerName;
    private String currencyCode;
    private BigDecimal amount;
    private BigDecimal platformFee;
    private BigDecimal feeDiff;
    private BigDecimal netAmount;
    private BigDecimal exchangeRate;
    /** 1-系统汇率、2-实际入账 */
    private Integer rateSource;
    private BigDecimal netAmountCny;
    private String paymentMethod;
    private String paymentMethodName;
    /** 1-线下、2-线上 */
    private Integer channel;
    private String platformOrderNo;
    private String payer;
    private LocalDate receiptDate;
    private String bankAccountName;
    private String note;
    /** 1-有效、2-已作废 */
    private Integer status;
    private String voidReason;
    private String operatorName;
    private String claimedByName;
    private LocalDateTime claimedAt;
    private LocalDateTime createTime;
}
