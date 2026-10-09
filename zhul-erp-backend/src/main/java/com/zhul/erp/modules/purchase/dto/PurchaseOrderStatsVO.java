package com.zhul.erp.modules.purchase.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 采购单统计（当前用户的数据范围） */
@Data
public class PurchaseOrderStatsVO {
    /** 本月已下单（按下单日期，不含已取消）金额（CNY）与张数 */
    private BigDecimal monthOrderedCny;
    private Long monthOrderedCount;
    /** 本月砍价合计（CNY）与整体砍价率（%） */
    private BigDecimal monthBargain;
    private BigDecimal monthBargainRate;
    private Long drafts;
    /** 已下单且还有未发数量 */
    private Long pendingShip;
    /** 其中已过预计发货日期 */
    private Long overdueShip;
    /** 放置超过 3 天的草稿 */
    private Long staleDrafts;
    /** 含「来源订单已取消」行的已下单采购单 */
    private Long orderCancelled;
}
