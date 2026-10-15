package com.zhul.erp.modules.inquiry.customerinquiry.dto;

import lombok.Data;

import java.util.List;

/** 询盘单详情 */
@Data
public class CustomerInquiryDetailVO {
    private CustomerInquiryVO inquiry;
    private String rawContent;
    private String parseError;
    private String remark;
    private List<AttachmentVO> attachments;
    private List<InquiryItemVO> items;
    private List<TaskBriefVO> tasks;
    /** 未完成的追加型号（解析中、待确认、解析失败），没有时为空 */
    private AppendVO openAppend;
    private String opportunityCode;
    private String opportunityStageName;
}
