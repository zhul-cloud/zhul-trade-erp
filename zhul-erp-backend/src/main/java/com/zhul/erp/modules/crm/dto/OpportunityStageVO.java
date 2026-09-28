package com.zhul.erp.modules.crm.dto;

import lombok.Data;

/** 阶段配置出参（前端画阶段条、筛选项用） */
@Data
public class OpportunityStageVO {

    private String code;
    private String name;
    /** 1-进行中、2-赢单、3-输单、4-无效 */
    private Integer category;
    private boolean countsAsValid;
    private Integer sortOrder;
}
