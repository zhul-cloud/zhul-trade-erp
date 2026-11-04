package com.zhul.erp.modules.crm.followup.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 跟进列表 / 今日待办的一行；金额都带 currencyCode */
@Data
public class FollowupItemVO {
    private Long id;
    private Integer board;
    /** 1-商机、2-询盘、3-报价单、4-PI */
    private Integer targetType;
    private Long targetId;
    private String docNo;
    /** 报价单 / PI 的当前版本号 */
    private Integer versionNo;
    private Long customerId;
    private String customerName;
    private String country;
    /** 经营等级：S / A / B / C，未成交为空 */
    private String bizGrade;
    /** 潜力 1–5，0 为未评 */
    private Integer potentialRating;
    private String stageCode;
    private String stageName;
    /** 1-报价前、2-报价后、3-PI 后 */
    private Integer stageGroup;
    private String currencyCode;
    private BigDecimal amount;
    private BigDecimal receivedAmount;
    private BigDecimal unpaidAmount;
    private BigDecimal amountCny;
    private LocalDateTime sentAt;
    /** 发出第几天（报价单、PI） */
    private Integer sentDays;
    /** PI：收款状态（1-未付款、2-待到账（已上传水单）、3-部分到账） */
    private Integer receiptStatus;
    private LocalDate expectedPayDate;
    private String blockerCode;
    private String blockerName;
    private LocalDateTime lastFollowupAt;
    private String lastChannelName;
    private String lastSummary;
    private String nextAction;
    private LocalDate nextActionDate;
    /** 1-系统提醒、2-我定的下一步、3-系统一次性提醒 */
    private Integer nextActionSource;
    /** 逾期天数，0 为没逾期 */
    private Integer overdueDays;
    private Integer snoozeCount;
    /** 连续延后达到 3 次（主管查看团队时标「多次延后」） */
    private Boolean manySnoozes;
    private Integer noReplyStreak;
    private LocalDate nurtureUntil;
    private String nurtureReason;
    private String nurtureReasonName;
    /** 报价后：发出超过 14 天或连续 3 次没回复，建议转培育 */
    private Boolean suggestNurture;
    /** SOP 建议（阶段默认动作） */
    private String sopHint;
    private Long ownerId;
    private String ownerName;
    private Long opportunityId;
}
