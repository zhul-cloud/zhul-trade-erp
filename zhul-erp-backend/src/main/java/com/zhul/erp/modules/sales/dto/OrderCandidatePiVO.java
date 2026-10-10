package com.zhul.erp.modules.sales.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 新建销售订单「按 PI 创建」的候选 PI：已发送、有水单或到账、没有未发送的新版本、还没转过订单 */
@Data
public class OrderCandidatePiVO {
    private Long id;
    private String piNo;
    private Integer versionNo;
    private String customerName;
    private String customerCountry;
    private String currencyCode;
    private BigDecimal totalAmount;
    private Integer receiptStatus;
    private String receiptStatusName;
    /** 最近一笔有效收款：1-水单、2-到账 */
    private Integer lastKind;
    private BigDecimal lastAmount;
    private LocalDate lastDate;
    /** 平台收款时为付款方式名称（如阿里巴巴信用保障） */
    private String lastMethodName;
    private Boolean lastPlatform;
    private String ownerName;
}
