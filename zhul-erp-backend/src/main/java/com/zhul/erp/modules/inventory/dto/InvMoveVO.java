package com.zhul.erp.modules.inventory.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 库存流水一行（库存流水页、单据详情的库存流水） */
@Data
public class InvMoveVO {
    private Long id;
    private LocalDateTime moveTime;
    private Long warehouseId;
    private String warehouseName;
    private Long productId;
    private String model;
    private String brand;
    private String category;
    private Long batchId;
    private String batchNo;
    private Integer stockStatus;
    private String stockStatusName;
    private Integer opType;
    private String opName;
    /** 带符号的数量：入为正、出为负；预占、释放只动预占 */
    private Integer quantity;
    private String currencyCode = "CNY";
    private BigDecimal unitPriceCny;
    private BigDecimal amountCny;
    private String bizType;
    private String bizName;
    private String bizLabel;
    private String docType;
    private Long docId;
    private String docNo;
    /** 预占对象：1-销售订单型号行、2-采购退货单行 */
    private Integer targetType;
    private Long targetId;
    /** 预占对象的单号（销售订单号或采购退货单号） */
    private String targetNo;
    private boolean reversal;
    private String operatorName;
}
