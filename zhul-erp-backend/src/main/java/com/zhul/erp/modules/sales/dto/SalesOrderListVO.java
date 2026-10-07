package com.zhul.erp.modules.sales.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 销售订单列表行 */
@Data
public class SalesOrderListVO {
    private Long id;
    private String soNo;
    private Long customerId;
    private String customerName;
    private Integer itemCount;
    private String currencyCode;
    private BigDecimal totalAmount;
    private Integer receiptStatus;
    private String receiptStatusName;
    private BigDecimal receivedAmount;
    private Integer status;
    private String statusName;
    private Long piId;
    private String piNo;
    private Integer piVersionNo;
    private Long ownerId;
    private String ownerName;
    private String cancelReason;
    private LocalDateTime createTime;
}
