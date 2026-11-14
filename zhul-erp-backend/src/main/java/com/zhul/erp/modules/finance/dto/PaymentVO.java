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
    /** 私对私时的付款个人账户 */
    private String payerAccount;
    /** 对方收款账户快照（只含后四位） */
    private String payeeAccount;
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

    // 以下只在详情接口返回（change finance-cash-docs）
    /** 附件（归属 PAYMENT，带预览信息） */
    private List<com.zhul.erp.modules.attachment.dto.AttachmentVO> files;
    private List<com.zhul.erp.modules.system.doclog.DocRefVO> related;
    private List<com.zhul.erp.modules.system.doclog.DocLogVO> logs;
    /** 当前用户能否改备注、增删附件（「付款管理」菜单、有效、非迁移） */
    private Boolean editable;
    /** 不能作废的原因（如已进入报销单 BXxxx），能作废或已作废时为空 */
    private String voidBlockedReason;

    @Data
    public static class Allocation {
        private Long payableId;
        private String payableNo;
        private String sourceNo;
        private BigDecimal amount;
        private BigDecimal amountCny;
        /** 0-实付、1-欠款抵扣、2-退款冲减 */
        private Integer fromCredit;
        private String creditPayableNo;
    }
}
