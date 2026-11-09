package com.zhul.erp.modules.crm.followup.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 记一次跟进（spec crm/customer-followup「记一次跟进」）。
 * itemId 为空时按 customerId 挂到该客户最紧急的跟进对象上；都没有时只写跟进记录。
 * situation 位掩码：1-客户给了反馈、2-客户在谈条件、4-没回复。profile 为勾选写入档案的字段（键 → 值，只补空值）。
 * <p>
 * 订单履约、售后评价阶段的字段（第二期 design D8）只能在对应阶段提交，否则报 FOLLOWUP_STAGE_MISMATCH：
 * noticeMask（F1–F6，1-运单照片、2-新交期、4-客户要配合、8-没回复）、etaDate（F6 改预计送达、A1 还没收到）、
 * receiptAnswer（A1，1-已收到无问题、2-有问题、3-还没收到）、useAnswer（A2，1-正常、2-有问题、3-还没开始用）、
 * issueTypes（A1、A2 有问题）、satisfaction / dissatisfiedReason / reviewStatus / reviewPlatform（A5、A6）。
 * 客户经营（第三期）：accountAnswer 必填，crossCategory、noNeedReason、quarterReview 按答案提交，其他板块提交报同样的错。
 */
@Data
public class RecordFollowupRequest {
    @NotNull(message = "请选择客户")
    private Long customerId;
    private Long itemId;
    /** 这次跟进涉及的单据（同一客户、同一板块，第一张为主单据）；传了就忽略 itemId */
    @Size(max = 500, message = "一次跟进最多选 500 张单据")
    private List<Long> itemIds;
    private Integer situation;
    private String blockerCode;
    @NotBlank(message = "请写下一步要做什么")
    @Size(max = 200, message = "下一步不能超过200个字")
    private String nextAction;
    @NotNull(message = "请选择下一步日期")
    private LocalDate nextActionDate;
    private Integer channel;
    private String rawContent;
    @Size(max = 1000, message = "摘要不能超过1000个字")
    private String summary;
    private Long aiTaskId;
    private LocalDate expectedPayDate;
    private Map<String, String> profile;
    private Integer noticeMask;
    private LocalDate etaDate;
    private Integer receiptAnswer;
    private Integer useAnswer;
    @Size(max = 4, message = "问题类型最多选 4 个")
    private List<String> issueTypes;
    private Integer satisfaction;
    @Size(max = 16, message = "原因不正确")
    private String dissatisfiedReason;
    private Integer reviewStatus;
    @Size(max = 32, message = "评价平台不能超过32个字")
    private String reviewPlatform;
    /** 客户经营（第三期 design D8）：1-客户有新需求、2-聊了新品类、3-当前没有需求、4-没回复 */
    private Integer accountAnswer;
    @Size(max = 50, message = "品类不能超过50个字")
    private String crossCategory;
    @Size(max = 16, message = "原因不正确")
    private String noNeedReason;
    private Boolean quarterReview;
    /** 主动开发（第四期 design D6）：1-已发出触达、2-客户回复了、3-出现采购信号、4-没回复、5-明确拒绝 */
    private Integer touchResult;
    @Size(max = 200, message = "型号和数量不能超过200个字")
    private String signalText;
    /** 前端每次打开弹窗生成一个，防重复提交 */
    @NotBlank(message = "缺少幂等键，请刷新后重试")
    @Size(max = 64, message = "幂等键过长")
    private String idempotencyKey;
}
