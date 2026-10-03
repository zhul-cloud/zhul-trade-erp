package com.zhul.erp.modules.inquiry.sourcing.dto;

import lombok.Data;

/** 分配工作台统计 */
@Data
public class BoardStatsVO {
    private Integer unassigned;
    private Long longestWaitingMinutes;
    private Integer sourcing;
    private Integer multiAssigned;
    private Integer timeout;
    private Integer returned;
    private Integer returnedForDoubt;
    /** 已回价、业务员还没报价的任务数（采购负责人还能看修改记录、调整成本价） */
    private Integer done;
    private Integer timeoutHours;
    private Integer urgentTimeoutHours;
    private Boolean autoAssign;
}
