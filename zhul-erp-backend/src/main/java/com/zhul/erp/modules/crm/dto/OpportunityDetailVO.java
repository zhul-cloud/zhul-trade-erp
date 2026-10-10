package com.zhul.erp.modules.crm.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.List;

/** 商机详情：列表字段 + 客户联系方式、结束信息、附件、阶段记录、关联询盘 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class OpportunityDetailVO extends OpportunityVO {

    private String customerCode;
    private String contactName;
    private String email;
    private String whatsapp;
    private String phone;
    private String website;
    /** 进入结束状态前的阶段（重新打开时回到这里），进行中为空串 */
    private String reopenStageCode;
    private Integer closeReason;
    private String closeReasonLabel;
    private String closeNote;
    private List<OpportunityAttachmentVO> attachments;
    private List<StageLogVO> stageLogs;
    private List<LinkedInquiryVO> inquiries;
}
