package com.zhul.erp.modules.crm.followup.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** AI 写一条英文回复：按客户、跟进对象和卡点生成；note 为业务员补充的意思（中文，可空） */
@Data
public class ReplyDraftRequest {
    @NotNull(message = "请选择客户")
    private Long customerId;
    private Long itemId;
    private String blockerCode;
    @Size(max = 500, message = "补充说明不能超过500个字")
    private String note;
    /** 意图：空为按卡点写跟进，logistics_update-物流更新、review_invite-评价邀请 */
    private String intent;
}
