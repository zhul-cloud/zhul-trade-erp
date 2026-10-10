package com.zhul.erp.modules.crm.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;

import java.time.LocalDate;
import java.util.List;

/** 登记商机：同时创建客户档案。联系人名称、国家、来源渠道、首次接触日期必填 */
@Data
public class RegisterOpportunityRequest {

    @NotBlank(message = "请填写联系人名称")
    @Size(max = 100, message = "联系人名称不能超过100个字符")
    private String contactName;

    @NotBlank(message = "请选择国家/地区")
    private String country;

    @NotNull(message = "请选择来源渠道")
    private Integer sourceChannel;

    @NotNull(message = "请选择首次接触日期")
    @PastOrPresent(message = "首次接触日期不能晚于今天")
    private LocalDate firstContactDate;

    @Size(max = 200, message = "客户名称不能超过200个字符")
    private String customerName;

    @Email(message = "请输入正确的邮箱格式")
    @Size(max = 100, message = "邮箱不能超过100个字符")
    @ToString.Exclude
    private String email;

    @Size(max = 30, message = "WhatsApp不能超过30个字符")
    @ToString.Exclude
    private String whatsapp;

    @Size(max = 32, message = "联系电话不能超过32个字符")
    @ToString.Exclude
    private String phone;

    @Size(max = 200, message = "官网不能超过200个字符")
    private String website;

    @Size(max = 2000, message = "需求摘要不能超过2000个字符")
    private String demandSummary;

    @Valid
    @Size(max = 10, message = "每条商机最多 10 个附件")
    private List<OpportunityAttachmentRequest> attachments;
}
