package com.zhul.erp.modules.crm.followup.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.Map;

/**
 * 记一次跟进（spec crm/customer-followup「记一次跟进」）。
 * itemId 为空时按 customerId 挂到该客户最紧急的跟进对象上；都没有时只写跟进记录。
 * situation 位掩码：1-客户给了反馈、2-客户在谈条件、4-没回复。profile 为勾选写入档案的字段（键 → 值，只补空值）。
 */
@Data
public class RecordFollowupRequest {
    @NotNull(message = "请选择客户")
    private Long customerId;
    private Long itemId;
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
    /** 前端每次打开弹窗生成一个，防重复提交 */
    @NotBlank(message = "缺少幂等键，请刷新后重试")
    @Size(max = 64, message = "幂等键过长")
    private String idempotencyKey;
}
