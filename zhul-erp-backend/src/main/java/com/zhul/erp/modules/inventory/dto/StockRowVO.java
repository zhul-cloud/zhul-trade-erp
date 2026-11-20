package com.zhul.erp.modules.inventory.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 库存查询一行：商品 × 仓库 */
@Data
public class StockRowVO {
    private Long productId;
    private String model;
    private String brand;
    private String category;
    private Long warehouseId;
    private String warehouseName;
    private Integer warehouseType;
    /** 合格结存、不良结存 */
    private Integer good;
    private Integer defective;
    /** 订单预占、退货待寄出（采购退货预占） */
    private Integer orderReserved;
    private Integer returnReserved;
    /** 可用 = 合格结存 − 合格预占 */
    private Integer available;
    /** 在途：只在公司仓库行，未收货的供应商发货单 */
    private Integer inTransit;
    private String currencyCode = "CNY";
    /** 库存金额（人民币）= Σ 结存 × 批次单价，HALF_UP 2 位 */
    private BigDecimal amountCny;
    private LocalDateTime lastMoveTime;
}
