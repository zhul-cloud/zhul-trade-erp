package com.zhul.erp.modules.inquiry.sourcing.dto;

import lombok.Data;

import java.util.List;

/** 分配规则 */
@Data
public class AssignRuleVO {
    private Long id;
    private Integer priority;
    private Integer matchType;
    private List<String> matchValues;
    private Long assigneeId;
    private String assigneeName;
    private Integer status;
    /** 近 30 天按该规则分配的任务数 */
    private Integer hits;
}
