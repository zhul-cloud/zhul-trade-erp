package com.zhul.erp.modules.quotation.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 报价单列表顶部统计（按当前用户数据范围） */
@Data
public class QuotationStatsVO {
    private Long draftCount;
    private Long sentCount;
    /** 已发送待回复里最久的天数 */
    private Long oldestSentDays;
    private Long monthWonCount;
    /** 本月成交合计（CNY） */
    private BigDecimal monthWonAmountCny;
    private BigDecimal monthWonNetProfitCny;
    /** 本月成交报价单的平均毛利率（%） */
    private BigDecimal monthAvgMargin;
    /** 本月新建、有型号低于红线的报价单数（不含作废） */
    private Long monthBelowFloorCount;
}
