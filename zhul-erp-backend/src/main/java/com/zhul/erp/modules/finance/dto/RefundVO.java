package com.zhul.erp.modules.finance.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 退款单 */
@Data
public class RefundVO {
    private Long id;
    private String refundNo;
    private Long soId;
    private String soNo;
    private String customerName;
    private LocalDate refundDate;
    private String reason;
    private String reasonName;
    private String currencyCode;
    private BigDecimal amount;
    private BigDecimal exchangeRate;
    private BigDecimal amountCny;
    private Integer method;
    private String remark;
    private Integer status;
    private String voidReason;
    private List<PayableVO.AttachmentRef> attachments;
    private LocalDateTime createTime;
    private String createBy;
    private LocalDateTime updateTime;
    private String updateBy;
    /** 历史迁移的单据（只读） */
    private Boolean migrated;
}
