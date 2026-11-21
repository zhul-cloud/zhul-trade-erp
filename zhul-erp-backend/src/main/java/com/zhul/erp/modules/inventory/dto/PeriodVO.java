package com.zhul.erp.modules.inventory.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 库存月结的一个月份（spec finance/period-close「库存月结页」）；已结账为结账时的数字，未结账为实时数字 */
@Data
public class PeriodVO {
    private String month;
    private Integer status;
    private String statusName;
    private String currencyCode = "CNY";
    private BigDecimal closingAmount;
    private BigDecimal cogsAmount;
    private BigDecimal adjustAmount;
    private String closedByName;
    private LocalDateTime closedAt;
    private String reopenedByName;
    private LocalDateTime reopenedAt;
    private String reopenReason;
    /** 现在能不能结账（已过完、前一个月已结账）；按钮另按权限显示 */
    private Boolean closable;
    /** 不能结账的原因（如「2026-12 还没过完」） */
    private String closeBlock;
    /** 是否最近一个已结账月份（只有它能反结账） */
    private Boolean reopenable;
    /** 结账前的提醒（不阻止结账），只在详情与结账确认里给出 */
    private List<String> reminders;
}
