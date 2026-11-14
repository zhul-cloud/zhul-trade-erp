package com.zhul.erp.modules.supply.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 覆盖渠道与它对该型号的最近证据；没有「查看货源信息」时不返回 */
@Data
public class ChannelVO {
    private Long supplierId;
    private String supplierName;
    private Integer role;
    /** 证据类型（1-询价记录、2-采购订单），没有证据为空 */
    private Integer evidenceType;
    private LocalDate evidenceDate;
    private BigDecimal unitPrice;
    private String currencyCode;
    private Boolean taxIncluded;
    private Integer itemCondition;
    private Integer leadTime;
}
