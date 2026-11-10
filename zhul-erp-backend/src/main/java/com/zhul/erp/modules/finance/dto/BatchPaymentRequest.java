package com.zhul.erp.modules.finance.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 批量付款（spec finance/supplier-payment「批量付款」）：每组一个收款方 + 币种，生成一笔付款单 */
@Data
public class BatchPaymentRequest {
    /** 1-公司对公、3-私对私 */
    @NotNull(message = "请选择付款方式")
    private Integer method;
    @NotNull(message = "请填写付款日期")
    private LocalDate payDate;
    @NotBlank(message = "缺少幂等键")
    @Size(max = 60)
    private String idempotencyKey;
    @NotEmpty(message = "请选择要付款的应付单")
    @Size(max = 50, message = "一次最多 50 组")
    @Valid
    private List<Group> groups;

    @Data
    public static class Group {
        @NotNull(message = "请选择我方付款账户")
        private Long bankAccountId;
        private Long payeeAccountId;
        @NotNull(message = "请填写付款金额")
        @DecimalMin(value = "0.01", message = "付款金额须大于 0")
        private BigDecimal amount;
        /** 实付人民币：外币必填 */
        private BigDecimal amountCny;
        @NotEmpty(message = "请上传付款截图")
        private List<Long> attachmentIds;
        @NotEmpty(message = "请选择要核销的应付单")
        private List<Long> payableIds;
    }
}
