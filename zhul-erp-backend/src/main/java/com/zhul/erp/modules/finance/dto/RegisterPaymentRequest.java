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

/** 登记付款：核销同一收款方、同一币种的应付单；可以用欠款抵扣 */
@Data
public class RegisterPaymentRequest {
    /** 1-公司对公、2-员工垫付、3-私对私 */
    @NotNull(message = "请选择付款方式")
    private Integer method;
    /** 公司对公必填 */
    private Long bankAccountId;
    /** 私对私必填：付款的个人账户，如「法人 张三 · 招行尾号 1234」 */
    @jakarta.validation.constraints.Size(max = 100, message = "付款个人账户不能超过 100 字")
    private String payerAccount;
    @NotNull(message = "请填写付款日期")
    private LocalDate payDate;
    /** 付款金额（原币，可以为 0：全部用欠款抵扣时） */
    @NotNull(message = "请填写付款金额")
    @DecimalMin(value = "0", message = "付款金额不能为负")
    private BigDecimal amount;
    /** 实付人民币：外币必填，人民币可不填（等于付款金额） */
    private BigDecimal amountCny;
    @Size(max = 500, message = "说明不能超过 500 字")
    private String remark;
    @NotEmpty(message = "请上传付款截图")
    private List<Long> attachmentIds;
    @NotEmpty(message = "请选择要核销的应付单")
    @Valid
    private List<Line> allocations;
    /** 欠款抵扣：从哪些应付单的欠款抵扣多少 */
    @Valid
    private List<Line> credits;
    @NotBlank(message = "缺少幂等键")
    @Size(max = 64)
    private String idempotencyKey;

    @Data
    public static class Line {
        @NotNull
        private Long payableId;
        @NotNull
        @DecimalMin(value = "0.01", message = "金额须大于 0")
        private BigDecimal amount;
    }
}
