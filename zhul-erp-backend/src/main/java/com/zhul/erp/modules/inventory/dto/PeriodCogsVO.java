package com.zhul.erp.modules.inventory.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 月份销售成本的一行：订单型号行 × 出运单（出运结转）或成本调整单（成本调整） */
@Data
public class PeriodCogsVO {
    private Integer kind;
    private String kindName;
    private Long soId;
    private String soNo;
    private Long soItemId;
    private Long productId;
    private String model;
    private String brand;
    private String category;
    /** SH 出运单 / CA 成本调整单 */
    private String docType;
    private Long docId;
    private String docNo;
    private LocalDate bizDate;
    private Integer quantity;
    private Integer systemQty;
    private String currencyCode = "CNY";
    private BigDecimal amountGoods;
    private BigDecimal amountInvoice;
    private BigDecimal amountFee;
    private BigDecimal amountClaim;
    private BigDecimal amountCny;
}
