package com.zhul.erp.modules.sales.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 销售订单详情：内容只读，收款记录来自 PI */
@Data
public class SalesOrderVO {
    private Long id;
    private String soNo;
    private Long piId;
    private String piNo;
    private Integer piVersionNo;
    private Integer piStatus;
    private Long customerId;
    private String customerName;
    private Long ownerId;
    private String ownerName;
    private String currencyCode;
    private BigDecimal exchangeRate;
    private PartyDTO buyer;
    private PartyDTO consignee;
    private String deliveryTime;
    private String paymentTerm;
    private String incoterm;
    private String incotermPlace;
    private String portOfShipment;
    private String remark;
    private BigDecimal itemAmount;
    private BigDecimal feeAmount;
    private BigDecimal discountAmount;
    private BigDecimal totalAmount;
    private BigDecimal totalAmountCny;
    private BigDecimal netProfit;
    private BigDecimal netProfitCny;
    private BigDecimal marginRate;
    /** 状态（1-有效、2-已取消） */
    private Integer status;
    private String statusName;
    private String cancelReason;
    private String cancelledByName;
    private LocalDateTime cancelledAt;
    private Integer receiptStatus;
    private String receiptStatusName;
    private BigDecimal receivedAmount;
    private BigDecimal feeDiffAmount;
    private BigDecimal remainingAmount;
    private List<PiItemVO> items;
    private List<PiFeeVO> fees;
    private List<ReceiptVO> receipts;
    private LocalDateTime createTime;
    private String createByName;
}
