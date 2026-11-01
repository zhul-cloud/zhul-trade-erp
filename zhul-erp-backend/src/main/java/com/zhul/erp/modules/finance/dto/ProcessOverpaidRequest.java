package com.zhul.erp.modules.finance.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 处理多收：转客户余额，或退回客户（转入后立即转给客户） */
@Data
public class ProcessOverpaidRequest {
    @NotBlank(message = "请选择单据")
    private String ownerType;
    @NotNull(message = "请选择单据")
    private Long ownerId;
    @NotNull(message = "请填写金额")
    @DecimalMin(value = "0.01", message = "金额须大于 0")
    private BigDecimal amount;
    /** CREDIT-转客户余额、REFUND-退回客户 */
    @NotBlank(message = "请选择处理方式")
    private String mode;
    /** 以下只用于退回客户 */
    private LocalDate withdrawDate;
    private Integer method;
    @Size(max = 32, message = "转出方式不能超过 32 个字符")
    private String methodName;
    @Size(max = 128, message = "收款账户不能超过 128 个字符")
    private String payeeAccount;
    private BigDecimal actualCny;
    private List<Long> attachmentIds;
    @Size(max = 500, message = "说明不能超过 500 个字符")
    private String remark;
}
