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

/** 员工登记已收到供应商退款：冲减欠款，成为本人待报销的负数项 */
@Data
public class ReceivedRefundRequest {
    @NotNull
    private Long payableId;
    @NotNull(message = "请填写退款金额")
    @DecimalMin(value = "0.01", message = "退款金额须大于 0")
    private BigDecimal amount;
    /** 外币应付：收到的人民币 */
    private BigDecimal amountCny;
    @NotNull(message = "请填写收到日期")
    private LocalDate receivedDate;
    @NotEmpty(message = "请上传收款截图")
    private List<Long> attachmentIds;
    @Size(max = 500)
    private String remark;
    @NotBlank(message = "缺少幂等键")
    @Size(max = 64)
    private String idempotencyKey;
}
