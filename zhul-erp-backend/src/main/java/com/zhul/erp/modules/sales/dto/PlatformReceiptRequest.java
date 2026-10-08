package com.zhul.erp.modules.sales.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 登记平台收款：线上付款方式、平台订单号、客户付款金额与平台手续费，登记即计入到账 */
@Data
public class PlatformReceiptRequest {
    private String paymentMethod;
    @Size(max = 64, message = "平台订单号不能超过 64 个字符")
    private String platformOrderNo;
    @NotNull(message = "请填写客户付款金额")
    private BigDecimal amount;
    private BigDecimal platformFee;
    @NotNull(message = "请选择到账日期")
    private LocalDate receiptDate;
    /** 已结汇时的实际入账人民币 */
    private BigDecimal actualAmountCny;
    @Size(max = 300, message = "说明不能超过 300 字")
    private String note;
}
