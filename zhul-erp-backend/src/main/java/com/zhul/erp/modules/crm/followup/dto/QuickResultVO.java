package com.zhul.erp.modules.crm.followup.dto;

import lombok.Data;

/** 一个快速跟进结果：编码、名称、推进到的阶段、推荐日期的修正方式（NORMAL / TOMORROW / NO_REPLY） */
@Data
public class QuickResultVO {
    private String code;
    private String label;
    private String advanceTo;
    private String dateRule;
}
