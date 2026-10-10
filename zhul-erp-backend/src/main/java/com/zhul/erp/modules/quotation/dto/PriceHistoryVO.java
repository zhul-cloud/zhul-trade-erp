package com.zhul.erp.modules.quotation.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 报价策略「沿用历史价」：同一客户同型号最近一次的成交价（优先）或报价 */
@Data
public class PriceHistoryVO {
    /** 本报价单的型号行 ID */
    private Long itemId;
    /** ORDER-成交、QUOTATION-报价 */
    private String kind;
    private String docNo;
    private Long docId;
    private LocalDate date;
    private String currencyCode;
    private BigDecimal unitPrice;
}
