package com.zhul.erp.modules.finance.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 登记结汇单：照银行结汇流水填写 */
@Data
public class SaveFxSettlementRequest {
    @NotNull(message = "请填写结汇日期")
    private LocalDate settleDate;
    @NotBlank(message = "请选择币种")
    private String currencyCode;
    @NotNull(message = "请填写结汇金额")
    @DecimalMin(value = "0.01", message = "结汇金额须大于 0")
    private BigDecimal amount;
    @NotNull(message = "请填写实际入账人民币")
    @DecimalMin(value = "0.01", message = "实际入账人民币须大于 0")
    private BigDecimal amountCny;
    @NotNull(message = "请选择入账账户")
    private Integer bankAccountId;
    @Size(max = 500, message = "说明不能超过 500 个字符")
    private String remark;
    /** 所属事业部（公司设置了事业部时必选，默认取入账账户的事业部）；只分摊本事业部的到账 */
    private Integer businessUnitId;
    @NotBlank(message = "缺少幂等键")
    @Size(max = 64, message = "幂等键过长")
    private String idempotencyKey;
}
