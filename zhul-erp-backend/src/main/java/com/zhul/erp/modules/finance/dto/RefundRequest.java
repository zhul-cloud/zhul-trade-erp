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

/** 供应商退款：冲减一张应付单的欠款，退到公司账户 */
@Data
public class RefundRequest {
    @NotNull
    private Long payableId;
    @NotNull(message = "请填写退款金额")
    @DecimalMin(value = "0.01", message = "退款金额须大于 0")
    private BigDecimal amount;
    /** 实收人民币：外币必填 */
    private BigDecimal amountCny;
    @NotNull(message = "请填写退款日期")
    private LocalDate refundDate;
    @NotNull(message = "请选择收款账户")
    private Long bankAccountId;
    @NotEmpty(message = "请上传退款截图")
    private List<Long> attachmentIds;
    @Size(max = 500)
    private String remark;
    @NotBlank(message = "缺少幂等键")
    @Size(max = 64)
    private String idempotencyKey;
}
