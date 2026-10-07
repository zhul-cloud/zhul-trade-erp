package com.zhul.erp.modules.sales.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 到账登记列表行：一张 PI 及其待确认水单 */
@Data
public class ReceiptDeskRowVO {
    private Long piId;
    private String piNo;
    private Integer piStatus;
    private Long customerId;
    private String customerName;
    private Long ownerId;
    private String ownerName;
    private String currencyCode;
    private BigDecimal totalAmount;
    private BigDecimal receivedAmount;
    private BigDecimal feeDiffAmount;
    private BigDecimal remainingAmount;
    private Integer receiptStatus;
    private String receiptStatusName;
    private Long orderId;
    private String soNo;
    /** 还没有对应有效到账的水单，按付款日期从早到晚 */
    private List<ReceiptVO> pendingSlips;
    private LocalDate earliestSlipDate;
}
