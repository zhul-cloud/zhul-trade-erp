package com.zhul.erp.modules.system.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/** 修改系统汇率 */
@Data
public class SaveExchangeRateRequest {
    @NotNull(message = "请填写汇率")
    private BigDecimal rate;
}
