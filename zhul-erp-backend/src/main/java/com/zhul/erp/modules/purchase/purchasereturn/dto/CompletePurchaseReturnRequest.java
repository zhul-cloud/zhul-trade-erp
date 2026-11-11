package com.zhul.erp.modules.purchase.purchasereturn.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 完成：签收日期必填；售后来源必须填退款或抵扣金额（采购订单币种，可以为 0） */
@Data
public class CompletePurchaseReturnRequest {
    @NotNull(message = "请填写签收日期")
    private LocalDate signedDate;
    private BigDecimal refundAmount;
    @Size(max = 300, message = "说明最多 300 字")
    private String settleNote;
}
