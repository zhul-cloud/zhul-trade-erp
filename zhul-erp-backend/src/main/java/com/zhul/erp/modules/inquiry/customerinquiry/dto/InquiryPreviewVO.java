package com.zhul.erp.modules.inquiry.customerinquiry.dto;

import lombok.Data;

import java.util.List;

/** 拆单预览响应（PRD 6.3.3）：直接渲染 ai_task.output，确认前不落地 inquiry_order。 */
@Data
public class InquiryPreviewVO {
    private CustomerInquiryVO inquiry;
    private List<AiParseGroupDTO> groups;
    private int totalItemCount;
    private int confirmedCount;
    private int correctedCount;
    private int pendingVerifyCount;
    private int unrecognizedCount;
}
