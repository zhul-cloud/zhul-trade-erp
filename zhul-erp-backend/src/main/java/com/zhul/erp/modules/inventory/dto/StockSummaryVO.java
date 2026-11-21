package com.zhul.erp.modules.inventory.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 库存查询顶部汇总（原型 I1）：库存金额（本位币，HALF_UP 两位）、可用、不良、退货待寄出 */
@Data
public class StockSummaryVO {
    private String currencyCode = "CNY";
    private BigDecimal amountCny = BigDecimal.ZERO;
    private Integer productCount = 0;
    private Integer warehouseCount = 0;
    private Integer available = 0;
    private Integer defective = 0;
    private Integer returnPending = 0;
}
