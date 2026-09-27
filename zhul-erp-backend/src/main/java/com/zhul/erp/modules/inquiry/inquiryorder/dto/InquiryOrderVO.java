package com.zhul.erp.modules.inquiry.inquiryorder.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class InquiryOrderVO {
    private Long id;
    private String inquiryCode;
    private Long customerInquiryId;
    private Long customerId;
    private String brand;
    private String category;
    private Integer itemCount;
    private Long assigneeId;
    private Integer status;
    private String inquiryTemplate;
    private String emailTemplateCn;
    private String emailTemplateEn;
    private Long aiTaskId;
    private String remark;
    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;
}
