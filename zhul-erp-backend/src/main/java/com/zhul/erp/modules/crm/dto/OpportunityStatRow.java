package com.zhul.erp.modules.crm.dto;

import lombok.Data;

/** 统计查询的一行（按渠道 / 业务员 / 日期分组） */
@Data
public class OpportunityStatRow {

    private String groupKey;
    private Long total;
    private Long invalid;
    private Long valid;
    private Long won;
    private Long lost;
}
