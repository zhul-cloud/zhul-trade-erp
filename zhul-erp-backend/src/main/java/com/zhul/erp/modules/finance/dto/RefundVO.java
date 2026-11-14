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
    /** 1-售后退款、2-余额提现 */
    private Integer refundType;
    private Long customerId;
    private Integer businessUnitId;
    private Long soId;
    private String soNo;
    /** 关联售后单与来源退款申请 */
    private Long afterSalesId;
    private String asNo;
    private Long refundRequestId;
    private String requestNo;
    private String customerName;
    private LocalDate refundDate;
    private String reason;
    private String reasonName;
    private String currencyCode;
    private BigDecimal amount;
    private BigDecimal exchangeRate;
    private BigDecimal amountCny;
    private Integer method;
    private String methodName;
    private String payeeAccount;
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

    // 以下只在详情接口返回（change finance-cash-docs）
    /** 明细：来源退款申请的明细；没有退款申请的为一行（余额提现或原因 + 金额） */
    private List<RefundRequestVO.Item> items;
    /** 附件（归属 REFUND，带预览信息） */
    private List<com.zhul.erp.modules.attachment.dto.AttachmentVO> files;
    private List<com.zhul.erp.modules.system.doclog.DocRefVO> related;
    private List<com.zhul.erp.modules.system.doclog.DocLogVO> logs;
    /** 当前用户能否改备注、增删附件（「退款管理」菜单、有效、非迁移） */
    private Boolean editable;
}
