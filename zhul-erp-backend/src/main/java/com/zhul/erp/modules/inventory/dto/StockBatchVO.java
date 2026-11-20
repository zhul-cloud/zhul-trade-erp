package com.zhul.erp.modules.inventory.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 库存查询展开的批次行（一个批次的一个状态） */
@Data
public class StockBatchVO {
    private Long batchId;
    private String batchNo;
    /** 来源：1-采购入库、2-退货入库、3-系统外入库、4-期初 */
    private Integer sourceType;
    private String sourceTypeName;
    private Long sourceId;
    private LocalDate inDate;
    private String grade;
    private Integer stockStatus;
    private String stockStatusName;
    private Integer onHand;
    private Integer reserved;
    /** 没有预占的数量（可以其他出库或退回供应商） */
    private Integer free;
    private String currencyCode = "CNY";
    private BigDecimal unitPriceCny;
    private BigDecimal amountCny;
    /** 采购批次的采购订单与供应商（退回供应商默认选它；线上店铺 supplierId=0、supplierName 为店铺名） */
    private Long poId;
    private String poNo;
    private Long supplierId;
    private String supplierName;
    private List<Reservation> reservations;

    @Data
    public static class Reservation {
        /** 1-销售订单型号行、2-采购退货单行 */
        private Integer targetType;
        private Long targetId;
        private String docNo;
        private Integer quantity;
    }
}
