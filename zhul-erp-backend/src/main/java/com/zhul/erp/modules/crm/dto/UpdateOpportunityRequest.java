package com.zhul.erp.modules.crm.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/** 编辑商机基本信息（客户信息在客户管理维护）；attachments 为 null 表示不修改附件 */
@Data
public class UpdateOpportunityRequest {

    @NotNull(message = "请选择来源渠道")
    private Integer sourceChannel;

    @NotNull(message = "请选择首次接触日期")
    @PastOrPresent(message = "首次接触日期不能晚于今天")
    private LocalDate firstContactDate;

    @Size(max = 2000, message = "需求摘要不能超过2000个字符")
    private String demandSummary;

    @Valid
    @Size(max = 10, message = "每条商机最多 10 个附件")
    private List<OpportunityAttachmentRequest> attachments;
}
