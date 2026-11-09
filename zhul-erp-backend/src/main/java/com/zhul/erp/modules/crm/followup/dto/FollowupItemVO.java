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
    /** 记跟进时「什么时候」推荐的日期与理由（spec「下一步日期推荐」） */
    private LocalDate suggestDate;
    private String suggestReason;
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

    // ---- 订单履约、售后评价（第二期）
    /** 现在的情况（系统带出） */
    private String situationText;
    /** 订单分几部分（按采购单 + 库存直发），2 以上可展开 */
    private Integer partCount;
    /** 履约异常位掩码：1-采购超期未到仓、2-有到货差异待处理、4-出库单已撤回 */
    private Integer exceptionFlags;
    private String exceptionText;
    private LocalDate exceptionSince;
    private LocalDate etaDate;
    /** 0-未出运、1-系统估算、2-业务员改的 */
    private Integer etaSource;
    /** 预计送达依据，如「DHL · 按 5 天估算」 */
    private String etaNote;
    private LocalDate signedDate;
    /** 签收第几天 */
    private Integer signedDays;
    private Integer satisfaction;
    private String satisfactionName;
    private Integer reviewStatus;
    private String reviewStatusName;
    private String reviewPlatform;
    /** A3、A4 对应的售后单 */
    private Long afterSalesId;
    private String afterSalesNo;
    private String afterSalesStatusName;
    /** 进入当前阶段的时间 */
    private LocalDateTime stageSince;
    /** 客户经营（第三期 spec「客户经营列表与作战卡」）：采购规律（系统推测）、近 12 个月成交（本位币，amountCny）、上次联系与等级间隔 */
    private String patternText;
    private String patternBrands;
    private LocalDate patternNextDate;
    private Integer recentOrderCount;
    private LocalDate lastOrderDate;
    private String accountReason;
    /** 1：记跟进选了「客户有新需求」还没建询盘，操作为「建询盘」 */
    private Integer accountNeed;
    private String crossCategory;
    private LocalDate dormantSince;
    private Integer dormantDays;
    private Boolean strategic;
    /** 该等级的联系间隔（天） */
    private Integer followDays;
    /** 上次联系日期（任意板块最近一条跟进记录，没有时为最近一单日期） */
    private LocalDate lastContactDate;
    /** 上次联系已超过等级间隔 */
    private Boolean contactOverdue;

    // ---- 主动开发（第四期）
    /** 为什么值得开发 */
    private String prospectReason;
    /** 主联系人（取自客户的主联系人字段）：姓名、职位、邮箱 */
    private String contactName;
    private String contactTitle;
    private String contactEmail;
    private String prospectSourceName;
    private Integer touchCount;
    private LocalDate firstTouchDate;
    /** 序列当前节点（第几天），未开始触达为 0 */
    private Integer sequenceDay;
    private String signalText;
    /** 最近一份已完成背调的跟进优先级：1-立即重点跟进、2-正常跟进、3-低优先级、4-不建议跟进；未出结论为空 */
    private Integer reportPriority;
}
