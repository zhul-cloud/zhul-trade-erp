package com.zhul.erp.modules.finance.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 把客户余额转给客户（生成余额提现退款单） */
@Data
public class WithdrawCreditRequest {
    @NotNull(message = "请选择客户")
    private Long customerId;
    @NotNull(message = "请选择币种")
    private String currencyCode;
    private Integer businessUnitId;
    @NotNull(message = "请填写转出金额")
    @DecimalMin(value = "0.01", message = "转出金额须大于 0")
    private BigDecimal amount;
    @NotNull(message = "请填写转出日期")
    private LocalDate withdrawDate;
    /** 2-银行汇款、4-其他（名称见 methodName，如支付宝、微信） */
    @NotNull(message = "请选择转出方式")
    private Integer method;
    @Size(max = 32, message = "转出方式不能超过 32 个字符")
    private String methodName;
    @Size(max = 128, message = "收款账户不能超过 128 个字符")
    private String payeeAccount;
    @NotNull(message = "请填写实际转出人民币")
    @DecimalMin(value = "0.01", message = "实际转出人民币须大于 0")
    private BigDecimal actualCny;
    @NotEmpty(message = "请上传转账凭证")
    private List<Long> attachmentIds;
    @Size(max = 500, message = "说明不能超过 500 个字符")
    private String remark;
}
