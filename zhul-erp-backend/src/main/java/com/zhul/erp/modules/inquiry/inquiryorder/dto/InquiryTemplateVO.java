package com.zhul.erp.modules.inquiry.inquiryorder.dto;

import lombok.Data;

/** 询价话术/邮件模版只读展示（任务5.9），不提供编辑接口。 */
@Data
public class InquiryTemplateVO {
    private String inquiryTemplate;
    private String emailTemplateCn;
    private String emailTemplateEn;
}
