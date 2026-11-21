package com.zhul.erp.modules.inventory.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 库存台账一行：某月某仓库（或全部）某商品的进销存 */
@Data
public class LedgerRowVO {
    private Long productId;
    private String model;
    private String brand;
    private String category;
    /** 一级品类 ID（0 为未分类）与名称；已结账月份取结账时的品类 */
    private Long categoryL1Id;
    private String categoryL1Name;
    private Long warehouseId;
    private String warehouseName;
    private Integer openingQty;
    private BigDecimal openingAmount;
    private Integer inQty;
    private BigDecimal inAmount;
    private Integer outQty;
    private BigDecimal outAmount;
    /** 本月成本调整金额（在库部分，见 spec finance/inventory-cost「成本调整」） */
    private BigDecimal adjustAmount;
    private Integer closingQty;
    private BigDecimal closingAmount;
    private String currencyCode = "CNY";
}
