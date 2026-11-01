package com.zhul.erp.modules.finance.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 提出退款申请前的可申请金额 */
@Data
public class RequestRefundableVO {
    private Long soId;
    private String soNo;
    private String currencyCode;
    /** 已到账货款（不含多收与代收） */
    private BigDecimal received;
    /** 已退售后退款 */
    private BigDecimal refunded;
    /** 待审批与待财务处理的申请合计 */
    private BigDecimal pending;
    /** 最多还能申请 */
    private BigDecimal available;
    /** 有平台收款：只能原路退回平台 */
    private Boolean platformOnly;
    /** 系统设置打开了审批 */
    private Boolean approvalRequired;
}
