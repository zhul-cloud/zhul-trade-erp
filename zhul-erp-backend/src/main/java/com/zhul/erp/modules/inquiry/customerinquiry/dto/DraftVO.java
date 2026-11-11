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
    /** 这次录入或追加的铭牌图片 */
    private List<AttachmentVO> nameplates;
    private List<DraftRowVO> rows;
    /** AI 没能解析的部分，确认页提示用户自行核对；手动录入或没有时为空 */
    private List<AiParseOutputDTO.Unparsed> unparsed = List.of();
}
