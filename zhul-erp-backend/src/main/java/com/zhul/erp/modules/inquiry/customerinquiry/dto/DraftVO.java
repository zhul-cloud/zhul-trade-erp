package com.zhul.erp.modules.inquiry.customerinquiry.dto;

import lombok.Data;

import java.util.List;

/** 解析确认页数据 */
@Data
public class DraftVO {
    private CustomerInquiryVO inquiry;
    private String rawContent;
    private List<AttachmentVO> attachments;
    private List<DraftRowVO> rows;
}
