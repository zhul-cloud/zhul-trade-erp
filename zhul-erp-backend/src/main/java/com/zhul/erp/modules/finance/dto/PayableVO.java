package com.zhul.erp.modules.finance.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 应付单（列表与详情共用；详情才有付款计划与付款单） */
@Data
public class PayableVO {
    private Long id;
    private String payableNo;
    /** 1-供应商、2-线上店铺、3-货代 */
    private Integer payeeType;
    private Long supplierId;
    private String shopPlatform;
    private String shopName;
    private String payeeName;
    /** 1-采购订单、2-运费账单 */
    private Integer sourceType;
    private Long sourceId;
    private String sourceNo;
    private String currencyCode;
    private BigDecimal amount;
    private BigDecimal exchangeRate;
    private BigDecimal amountCny;
    private BigDecimal paidAmount;
    private BigDecimal paidCny;
    private BigDecimal unpaidAmount;
    private BigDecimal creditAmount;
    /** 汇兑差异（人民币）：已付人民币 − 已付原币按应付汇率折算，只显示 */
    private BigDecimal exchangeDiff;
    /** 1-未付、2-部分付款、3-已付清、4-已关闭 */
    private Integer status;
    private LocalDate nextDueDate;
    /** OVERDUE 已逾期、SOON 即将到期、WAITING 只剩待入库、空 */
    private String dueState;
    /** 逾期天数或到期剩余天数 */
    private Integer dueDays;
    private String paymentTerms;
    private List<Installment> installments = new ArrayList<>();
    private List<PaymentLine> payments = new ArrayList<>();
    private LocalDateTime createTime;
    private String createBy;
    private LocalDateTime updateTime;
    private String updateBy;

    @Data
    public static class Installment {
        private Integer seq;
        private BigDecimal ratio;
        private Integer triggerType;
        private Integer offsetDays;
        /** 下单后 / 发货前 / 入库后 30 天 / 对账确认 */
        private String triggerName;
        private BigDecimal amount;
        private LocalDate dueDate;
        private BigDecimal paidAmount;
        private Integer status;
        private String dueState;
        private Integer dueDays;
    }

    /** 与本单有关的付款：核销进来、欠款抵扣出去、退款 */
    @Data
    public static class PaymentLine {
        private Long paymentId;
        private String paymentNo;
        /** 1-付款、2-供应商退款 */
        private Integer kind;
        /** 0-实付核销、1-欠款抵扣转入、2-退款冲减、3-欠款抵扣转出 */
        private Integer relation;
        private Integer method;
        private String accountName;
        private String advanceUser;
        private LocalDate payDate;
        private BigDecimal amount;
        private BigDecimal amountCny;
        private String otherPayableNo;
        private boolean voided;
        private String createBy;
        private List<AttachmentRef> attachments = new ArrayList<>();
    }

    @Data
    public static class AttachmentRef {
        private Long id;
        private String fileName;
        private Integer kind;
    }
}
