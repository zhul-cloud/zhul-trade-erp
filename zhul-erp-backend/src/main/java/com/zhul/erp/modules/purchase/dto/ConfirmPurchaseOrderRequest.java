package com.zhul.erp.modules.purchase.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class ConfirmPurchaseOrderRequest {
    @NotNull(message = "请填写下单日期")
    private LocalDate orderDate;
    @NotNull(message = "请填写预计发货日期")
    private LocalDate expectedShipDate;
}
