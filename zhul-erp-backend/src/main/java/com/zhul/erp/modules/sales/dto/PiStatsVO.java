package com.zhul.erp.modules.sales.dto;

import lombok.Data;
import java.math.BigDecimal;

/** PI 列表顶部统计 */
@Data
public class PiStatsVO {
    private Long draftCount;
    /** 有水单待到账的 PI */
    private Long slipOnlyCount;
    /** 本月到账（美元） */
    private BigDecimal monthReceivedUsd;
    /** 本月到账（人民币） */
    private BigDecimal monthReceivedCny;
    private Long monthOrderCount;
    /** 本月凭水单先转、还未到账的订单 */
    private Long monthOrderUnpaid;
}
