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
    private Long warehouseId;
    private String warehouseName;
    private Integer openingQty;
    private BigDecimal openingAmount;
    private Integer inQty;
    private BigDecimal inAmount;
    private Integer outQty;
    private BigDecimal outAmount;
    private Integer closingQty;
    private BigDecimal closingAmount;
    private String currencyCode = "CNY";
}
