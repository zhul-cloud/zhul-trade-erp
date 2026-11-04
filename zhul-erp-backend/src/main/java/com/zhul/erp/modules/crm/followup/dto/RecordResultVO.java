package com.zhul.erp.modules.crm.followup.dto;

import lombok.Data;

/** 记一次跟进的结果：保存后的效果提示 */
@Data
public class RecordResultVO {
    private Long recordId;
    private String stageBefore;
    private String stageAfter;
    /** 商机是否被推进 */
    private Boolean opportunityAdvanced;
    /** 重复提交时返回第一次的结果 */
    private Boolean duplicate;
}
