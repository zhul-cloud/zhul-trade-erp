package com.zhul.erp.modules.finance.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 登记付款时可选的收款方：有未付应付或欠款的 */
@Data
public class PayeeOptionVO {
    /** 收款方键：payeeType:supplierId 或 2:平台:店铺名 */
    private String key;
    /** 该收款方的任一张应付单 ID：用它取同一收款方的未付应付单 */
    private Long anchorId;
    private Integer payeeType;
    private Long supplierId;
    private String shopPlatform;
    private String shopName;
    private String payeeName;
    private String currencyCode;
    private BigDecimal unpaid;
    private BigDecimal credit;
}
