package com.zhul.erp.modules.crm.followup.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 今日待办：已逾期、今天两组，组内按客户价值排序 */
@Data
public class FollowupTodayVO {
    private List<FollowupItemVO> overdue = new ArrayList<>();
    private List<FollowupItemVO> today = new ArrayList<>();
    /** 本周还有（明天到周日） */
    private Integer weekRemaining;
    /** 明天要跟（空态提示用） */
    private Integer tomorrow;
}
