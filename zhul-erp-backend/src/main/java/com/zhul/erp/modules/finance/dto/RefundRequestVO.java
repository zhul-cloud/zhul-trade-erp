package com.zhul.erp.modules.finance.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 退款申请 */
@Data
public class RefundRequestVO {
    private Long id;
    private String requestNo;
    /** 来源：销售退货单（有时）或售后单 */
    private Long salesReturnId;
    private String sourceNo;
    private Integer refundFirst;
    /** 实收少于退货数量时的提示 */
    private String receivedHint;
    /** 退款单金额（执行时改过金额的，与申请金额的差额在详情页显示） */
    private BigDecimal refundAmount;
    private java.util.List<Item> items;
    private java.util.List<com.zhul.erp.modules.attachment.dto.AttachmentVO> attachments;
    private java.util.List<com.zhul.erp.modules.system.doclog.DocLogVO> logs;
    private Long afterSalesId;
    private String asNo;
    private Long soId;
    private String soNo;
    private String customerName;
    /** 客户主联系人姓名（客户档案中的主联系人，实时读取） */
    private String customerContact;
    /** 客户国家 / 地区（列表客户列第二行） */
    private String customerCountry;
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

    @Data
    public static class Item {
        private Long id;
        private Integer lineType;
        private Long soItemId;
        private Long salesReturnItemId;
        private String itemName;
        private String brand;
        private String category;
        private Integer quantity;
        private BigDecimal unitPrice;
        private BigDecimal amount;
        private String note;
    }
}
