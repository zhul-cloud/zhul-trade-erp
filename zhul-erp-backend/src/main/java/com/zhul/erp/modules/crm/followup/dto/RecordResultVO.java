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
    /** A1 已收到无问题时订单是否确认为客户已收货；false 时前端提示「订单还有型号没出运，没有标为已收货」；其他情况为 null */
    private Boolean orderCompleted;
    /** 记完后跟进对象是否已结束（售后结束） */
    private Boolean ended;
    /** 重复提交时返回第一次的结果 */
    private Boolean duplicate;
}
