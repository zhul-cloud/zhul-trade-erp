package com.zhul.erp.modules.crm.dto;

import lombok.Data;

/** 商机列表顶部概览（按数据权限统计） */
@Data
public class OpportunitySummaryVO {

    private long todayNew;
    private long todayInvalid;
    private long weekNew;
    private long lastWeekNew;
    /** 当前处于 S1 的商机数 */
    private long firstStageCount;
    /** 其中超过 2 天未推进的 */
    private long firstStageStale;
    /** 本月新增与其中有效数，前端算有效率 */
    private long monthNew;
    private long monthValid;
}
