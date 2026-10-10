package com.zhul.erp.modules.inquiry.customerinquiry.dto;

import lombok.Data;

import java.util.List;

/** 客户询盘详情 */
@Data
public class CustomerInquiryDetailVO {
    private CustomerInquiryVO inquiry;
    private String rawContent;
    private String parseError;
    private String remark;
    private List<AttachmentVO> attachments;
    private List<InquiryItemVO> items;
    private List<TaskBriefVO> tasks;
    private String opportunityCode;
    private String opportunityStageName;
}
