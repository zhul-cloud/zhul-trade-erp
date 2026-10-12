package com.zhul.erp.modules.finance.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 付款单（含供应商退款） */
@Data
public class PaymentVO {
    private Long id;
    private String paymentNo;
    private Integer kind;
    private Integer payeeType;
    private String payeeName;
    private Integer method;
    private Long bankAccountId;
    private String accountName;
    private String advanceUser;
    private Integer reimburseStatus;
    private LocalDate payDate;
    private String currencyCode;
    private BigDecimal amount;
    private BigDecimal amountCny;
    private BigDecimal actualRate;
    private BigDecimal creditUsed;
    private String remark;
    private boolean voided;
    private String voidBy;
    private String voidReason;
    private LocalDateTime voidAt;
    /** 核销的应付单（编号 + 金额） */
    private List<Allocation> allocations = new ArrayList<>();
    private List<PayableVO.AttachmentRef> attachments = new ArrayList<>();
    /** 当前用户能否作废 */
    private boolean canVoid;
    private LocalDateTime createTime;
    private String createBy;
    private LocalDateTime updateTime;
    private String updateBy;

    @Data
    public static class Allocation {
        private Long payableId;
        private String payableNo;
        private String sourceNo;
        private BigDecimal amount;
        /** 0-实付、1-欠款抵扣、2-退款冲减 */
        private Integer fromCredit;
        private String creditPayableNo;
    }
}
