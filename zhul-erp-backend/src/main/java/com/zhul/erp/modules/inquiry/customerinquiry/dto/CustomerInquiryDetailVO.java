package com.zhul.erp.modules.inquiry.customerinquiry.dto;

import com.zhul.erp.modules.aitask.dto.AiFailover;
import lombok.Data;

import java.util.List;

/** 询盘单详情 */
@Data
public class CustomerInquiryDetailVO {
    private CustomerInquiryVO inquiry;
    private String rawContent;
    private String parseError;
    /** 解析中途自动切换了 AI 连接方式时：最后使用的方式（如 api:openai）与切换记录；没有切换时为空 */
    private String parseUsedConnection;
    private List<AiFailover> parseFailovers;
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
