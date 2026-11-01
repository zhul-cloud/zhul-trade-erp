package com.zhul.erp.modules.finance.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 待处理多收：PI 或手动订单的已到账超过合计、还没转余额或退回的部分 */
@Data
public class OverpaidVO {
    /** PI 或 SO */
    private String ownerType;
    private Long ownerId;
    private String ownerNo;
    private Long soId;
    private String soNo;
    private Long customerId;
    private String customerName;
    private String currencyCode;
    private Integer businessUnitId;
    private BigDecimal total;
    private BigDecimal received;
    private BigDecimal overpaid;
    private BigDecimal pending;
    /** 现金收款的加权到账汇率与待处理多收的估算人民币 */
    private BigDecimal rate;
    private BigDecimal pendingCny;
    /** 业务员（PI 或订单的负责人） */
    private Long salesId;
    private String salesName;
}
