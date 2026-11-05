package com.zhul.erp.modules.crm.followup.dto;

import lombok.Data;

/** 英文回复草稿：不自动发送，不记为一次跟进 */
@Data
public class ReplyDraftVO {
    private String reply;
    /** 这条消息在说什么（中文） */
    private String intent;
}
