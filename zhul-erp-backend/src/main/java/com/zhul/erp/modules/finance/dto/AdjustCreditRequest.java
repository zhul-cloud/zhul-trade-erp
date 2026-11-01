package com.zhul.erp.modules.finance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** 手工调整客户余额：金额可正可负，不能为 0；人民币价值只用于正数调整（负数按先进先出取锁定价值） */
@Data
public class AdjustCreditRequest {
    @NotNull(message = "请选择客户")
    private Long customerId;
    @NotBlank(message = "请选择币种")
    private String currencyCode;
    private Integer businessUnitId;
    @NotNull(message = "请填写调整金额")
    private BigDecimal amount;
    private BigDecimal amountCny;
    @NotBlank(message = "请填写调整原因")
    @Size(max = 500, message = "调整原因不能超过 500 个字符")
    private String reason;
    private List<Long> attachmentIds;
}
