package com.zhul.erp.modules.sales.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/** 手动订单的费用行（普通费用，币种同订单） */
@Data
public class OrderFeeLine {
    @NotBlank(message = "请填写费用名称")
    @Size(max = 64, message = "费用名称不能超过 64 个字符")
    private String feeName;
    @NotNull(message = "请填写费用金额")
    @DecimalMin(value = "0.01", message = "费用金额需要大于 0")
    private BigDecimal amount;
}
