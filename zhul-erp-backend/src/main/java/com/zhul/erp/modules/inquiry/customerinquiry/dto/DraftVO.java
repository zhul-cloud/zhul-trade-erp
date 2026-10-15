package com.zhul.erp.modules.inquiry.customerinquiry.dto;

import lombok.Data;

import java.util.List;

/** 解析确认页数据 */
@Data
public class DraftVO {
    private CustomerInquiryVO inquiry;
    /** 追加型号的批次 ID；首次确认时为空 */
    private Long appendId;
    private String rawContent;
    private List<AttachmentVO> attachments;
    private List<DraftRowVO> rows;
}
