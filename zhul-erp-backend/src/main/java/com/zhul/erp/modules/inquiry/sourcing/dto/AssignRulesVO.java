package com.zhul.erp.modules.inquiry.sourcing.dto;

import lombok.Data;

import java.util.List;

/** 分配规则页 */
@Data
public class AssignRulesVO {
    private Boolean autoAssign;
    private List<AssignRuleVO> rules;
}
