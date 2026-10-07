package com.zhul.erp.modules.quotation.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 报价单列表行 */
@Data
public class QuotationListVO {
    private Long id;
    private String quotationNo;
    private Long customerId;
    private String customerName;
    private Integer itemCount;
    private String currencyCode;
    private BigDecimal totalAmount;
    private BigDecimal marginRate;
    private Integer status;
    private String statusName;
    private String lostReasonName;
    private List<String> inquiryCodes;
    private Long ownerId;
    private String ownerName;
    private LocalDateTime createTime;
    private LocalDateTime sentAt;
}
