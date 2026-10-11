package com.zhul.erp.modules.finance.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 报销单 */
@Data
public class ReimbursementVO {
    private Long id;
    private String reimburseNo;
    private Long applicantId;
    private String applicantName;
    private LocalDateTime submittedAt;
    private Integer lineCount;
    private BigDecimal totalAmount;
    /** 1-待付款、2-已报销、3-已撤回 */
    private Integer status;
    private String withdrawReason;
    private Long payAccountId;
    private String payAccountName;
    private LocalDate payDate;
    private String paidBy;
    private LocalDateTime paidAt;
    private String payRemark;
    private String paymentNo;
    private List<PayableVO.AttachmentRef> payProofs = new ArrayList<>();
    private List<AdvanceItemVO> lines = new ArrayList<>();
    /** 当前用户能否撤回（本人、待付款） */
    private boolean canWithdraw;
    private LocalDateTime createTime;
    private String createBy;
    private LocalDateTime updateTime;
    private String updateBy;
}
