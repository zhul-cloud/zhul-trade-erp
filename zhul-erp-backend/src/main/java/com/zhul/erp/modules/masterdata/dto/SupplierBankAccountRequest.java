package com.zhul.erp.modules.masterdata.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;

/**
 * 一个收款账户。带 id 表示更新已有账户，不带表示新增。
 * payeeIdNo：为 null 时沿用原值（从不回传明文），空串表示清空，有值则覆盖。
 */
@Data
public class SupplierBankAccountRequest {

    private Long id;

    /** 账户类型（1-对公、2-对私） */
    @NotNull(message = "请选择账户类型")
    @Min(value = 1, message = "账户类型不正确")
    @Max(value = 2, message = "账户类型不正确")
    private Integer accountType;

    @NotBlank(message = "请填写户名")
    @Size(max = 100, message = "户名不能超过100个字符")
    private String accountName;

    @NotBlank(message = "请填写开户银行")
    @Size(max = 100, message = "开户银行不能超过100个字符")
    private String bankName;

    @NotNull(message = "请填写账号")
    @Pattern(regexp = "^\\d{8,30}$", message = "账号只能包含数字，长度 8 到 30 位")
    @ToString.Exclude
    private String accountNo;

    @Pattern(regexp = "^(1\\d{10})?$", message = "请输入正确的 11 位手机号")
    @ToString.Exclude
    private String payeePhone;

    @Pattern(regexp = "^(\\d{17}[\\dXx])?$", message = "请输入正确的 18 位身份证号")
    @ToString.Exclude
    private String payeeIdNo;

    /** 是否默认收款账户 */
    private boolean defaultAccount;
}
