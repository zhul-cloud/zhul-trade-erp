package com.zhul.erp.modules.purchase.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 登记已开票：实际开票日期、电子发票，可以按实际发票调整开票金额与税点 */
@Data
public class RegisterInvoiceRequest {
    @NotNull(message = "请填写实际开票日期")
    private LocalDate invoicedDate;
    @NotNull(message = "请填写开票金额")
    @DecimalMin(value = "0.01", message = "开票金额须大于 0")
    private BigDecimal invoiceAmount;
    @NotNull(message = "请填写税点")
    @DecimalMin(value = "0", message = "税点不能为负")
    @DecimalMax(value = "100", message = "税点不能超过 100%")
    private BigDecimal taxPoint;
    @NotEmpty(message = "请上传电子发票")
    private List<Long> attachmentIds;
}
