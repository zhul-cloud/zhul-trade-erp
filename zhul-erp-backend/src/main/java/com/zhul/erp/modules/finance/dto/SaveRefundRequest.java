package com.zhul.erp.modules.finance.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 登记退款单 */
@Data
public class SaveRefundRequest {
    @NotNull(message = "请选择销售订单")
    private Long soId;
    @NotNull(message = "请填写退款日期")
    private LocalDate refundDate;
    @NotBlank(message = "请选择退款原因")
    private String reason;
    @NotNull(message = "请填写退款金额")
    @DecimalMin(value = "0.01", message = "退款金额须大于 0")
    private BigDecimal amount;
    /** 实际退出人民币；为空时按订单平均到账汇率折算 */
    @DecimalMin(value = "0.01", message = "实际退出人民币须大于 0")
    private BigDecimal amountCny;
    @NotNull(message = "请选择退款方式")
    private Integer method;
    @Size(max = 500, message = "说明不能超过 500 个字符")
    private String remark;
    @NotEmpty(message = "请上传退款凭证")
    private List<Long> attachmentIds;
}
