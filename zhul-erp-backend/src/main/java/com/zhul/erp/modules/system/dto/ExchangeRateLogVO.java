package com.zhul.erp.modules.system.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 汇率变更记录 */
@Data
public class ExchangeRateLogVO {
    private String currencyCode;
    private BigDecimal oldRate;
    private BigDecimal newRate;
    private String operatorName;
    private LocalDateTime operatedAt;
}
