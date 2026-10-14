package com.zhul.erp.modules.finance.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 订单可退金额：已到账、已退款、最多还能退与平均到账汇率 */
@Data
public class RefundableVO {
    private Long soId;
    private String soNo;
    private String currencyCode;
    private BigDecimal received;
    private BigDecimal refunded;
    private BigDecimal refundable;
    private BigDecimal averageRate;
}
