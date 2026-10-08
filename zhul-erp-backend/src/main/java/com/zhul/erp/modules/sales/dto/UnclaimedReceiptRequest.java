package com.zhul.erp.modules.sales.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 登记未认领到账：还不知道属于哪张 PI 的到账 */
@Data
public class UnclaimedReceiptRequest {
    @NotBlank(message = "请选择币种")
    private String currencyCode;
    @NotNull(message = "请填写到账金额")
    private BigDecimal amount;
    @NotNull(message = "请选择到账日期")
    private LocalDate receiptDate;
    @NotNull(message = "请选择收款账户")
    private Integer bankAccountId;
    private String paymentMethod;
    @Size(max = 128, message = "付款人不能超过 128 个字符")
    private String payer;
    private BigDecimal actualAmountCny;
    @Size(max = 300, message = "说明不能超过 300 字")
    private String note;
}
