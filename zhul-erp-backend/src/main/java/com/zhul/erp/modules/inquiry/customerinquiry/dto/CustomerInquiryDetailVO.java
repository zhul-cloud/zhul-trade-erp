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
    /** 铭牌图片（含追加的），inquiryItemId 为对应的型号 */
    private List<AttachmentVO> nameplates;
    private List<InquiryItemVO> items;
    private List<TaskBriefVO> tasks;
    /** 未完成的追加型号（解析中、待确认、解析失败），没有时为空 */
    private AppendVO openAppend;
    private String opportunityCode;
    private String opportunityStageName;
}
