package com.zhul.erp.modules.finance.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 退款申请 */
@Data
public class RefundRequestVO {
    private Long id;
    private String requestNo;
    private Long afterSalesId;
    private String asNo;
    private Long soId;
    private String soNo;
    private String customerName;
    /** 客户主联系人姓名（客户档案中的主联系人，实时读取） */
    private String customerContact;
    private String currencyCode;
    private BigDecimal amount;
    private String reason;
    private String reasonName;
    private Integer method;
    private String methodName;
    private String payeeInfo;
    private String remark;
    private Integer status;
    private String statusName;
    private String applicantName;
    private String approverName;
    private LocalDateTime approvedAt;
    private String handlerName;
    private LocalDateTime handledAt;
    private String rejectReason;
    private Long refundId;
    private String refundNo;
    private LocalDateTime createTime;
    private String createBy;
    private LocalDateTime updateTime;
    private String updateBy;
}
