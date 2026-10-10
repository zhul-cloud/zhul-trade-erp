package com.zhul.erp.modules.system.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 系统汇率：没有设置时 rate 为空 */
@Data
public class ExchangeRateVO {
    private String currencyCode;
    private BigDecimal rate;
    private String sourceName;
    private String updatedByName;
    private LocalDateTime rateTime;
}
