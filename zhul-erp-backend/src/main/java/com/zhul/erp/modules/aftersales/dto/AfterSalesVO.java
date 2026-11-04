package com.zhul.erp.modules.aftersales.dto;

import com.zhul.erp.modules.attachment.dto.AttachmentVO;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 售后单（列表只填基本字段，详情带型号、时间线与汇总） */
@Data
public class AfterSalesVO {
    private Long id;
    private String asNo;
    private Long customerId;
    private String customerName;
    private Long soId;
    private String soNo;
    private String currencyCode;
    private String levelCode;
    private String levelName;
    private String symptom;
    private String customerRequest;
    private LocalDateTime receivedAt;
    private String reasonCode;
    private String reasonName;
    private List<String> solutions;
    private List<String> solutionNames;
    private Long ownerId;
    private String ownerName;
    private String nextAction;
    private LocalDateTime nextUpdateAt;
    /** 已过截止时间（处理中、待客户确认时） */
    private Boolean overdue;
    /** 处理时限（spec sales/after-sales「处理时限」） */
    private LocalDateTime respondedAt;
    private LocalDateTime lastContactAt;
    /** 按等级算出的截止时间（不含手填时间） */
    private LocalDateTime levelDueAt;
    private LocalDateTime dueAt;
    /** 0-无、1-首次响应、2-更新客户、3-手填时间 */
    private Integer dueType;
    private String dueTypeName;
    /** OVERDUE-已逾期、SOON-快到期、NORMAL-正常；没有截止时间为空 */
    private String dueState;
    private Integer status;
    private String statusName;
    private String result;
    /** 案例沉淀 */
    private String caseNote;
    private LocalDateTime closedAt;
    private String cancelReason;
    /** 型号摘要（列表用） */
    private String itemSummary;
    private List<Item> items;
    private List<AttachmentVO> attachments;
    private List<Log> logs;
    private List<ReturnBrief> returns;
    private List<RequestBrief> requests;
    private List<RefundBrief> refunds;
    private List<ExpenseBrief> expenses;
    /** 售后补发订单 */
    private List<ReplacementBrief> replacements;
    /** 已发生的人民币：有效退款 + 有效费用 */
    private BigDecimal refundCny;
    private BigDecimal expenseCny;
    /** 退货去向冲减原订单成本（人民币，正数为冲减）与退回运费 */
    private BigDecimal returnCreditCny;
    private BigDecimal returnFreightCny;
    /** 当前用户能编辑（负责人、登记人或有全部数据范围） */
    private Boolean editable;
    private LocalDateTime createTime;
    private String createBy;
    private LocalDateTime updateTime;
    private String updateBy;

    @Data
    public static class ReplacementBrief {
        private Long soId;
        private String soNo;
        /** 型号摘要 */
        private String summary;
        private String currencyCode;
        /** 申报货值合计（原币） */
        private BigDecimal declaredAmount;
        /** 1-有效、2-已取消 */
        private Integer status;
        private String progressCode;
        private String progressName;
        /** 已出运（或之后）且未取消 */
        private Boolean shipped;
        private String createBy;
        private LocalDateTime createTime;
    }

    @Data
    public static class Item {
        private Long id;
        private Long soItemId;
        private String model;
        private String brand;
        private Integer quantity;
        /** 订单型号已出运数量（退货上限参考） */
        private Integer shippedQty;
    }

    @Data
    public static class Log {
        private Long id;
        private String logType;
        private String logTypeName;
        private String content;
        private Boolean customerNotified;
        private String operatorName;
        private LocalDateTime createTime;
        private List<AttachmentVO> attachments;
    }

    @Data
    public static class ReturnBrief {
        private Long id;
        private String returnNo;
        private Integer status;
        private String statusName;
        private String summary;
    }

    @Data
    public static class RequestBrief {
        private Long id;
        private String requestNo;
        private Integer status;
        private String statusName;
        private String currencyCode;
        private BigDecimal amount;
    }

    @Data
    public static class RefundBrief {
        private Long id;
        private String refundNo;
        private LocalDate refundDate;
        private String currencyCode;
        private BigDecimal amount;
        private BigDecimal amountCny;
        private String methodName;
    }

    @Data
    public static class ExpenseBrief {
        private Long id;
        private String expenseNo;
        private String name;
        private BigDecimal amountCny;
    }
}
