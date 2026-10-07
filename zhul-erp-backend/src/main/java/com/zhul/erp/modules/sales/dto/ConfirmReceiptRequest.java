package com.zhul.erp.modules.sales.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 财务登记到账 */
@Data
public class ConfirmReceiptRequest {
    @NotNull(message = "请填写到账金额")
    private BigDecimal amount;
    @NotNull(message = "请选择到账日期")
    private LocalDate receiptDate;
    @NotNull(message = "请选择收款账户")
    private Integer bankAccountId;
    /** 对应的水单，可不选 */
    private Long slipId;
    /** 本次到账后的剩余差额记为银行中转手续费 */
    private Boolean feeDiff;
    @Size(max = 300, message = "说明不能超过 300 字")
    private String note;
}
