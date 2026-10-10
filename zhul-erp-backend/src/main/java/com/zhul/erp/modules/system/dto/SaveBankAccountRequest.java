package com.zhul.erp.modules.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 新增 / 修改收款账户 */
@Data
public class SaveBankAccountRequest {
    @NotBlank(message = "请选择币种")
    private String currencyCode;
    @NotBlank(message = "请填写银行名称")
    @Size(max = 128, message = "银行名称不能超过 128 个字符")
    private String bankName;
    @NotBlank(message = "请填写账户名称")
    @Size(max = 128, message = "账户名称不能超过 128 个字符")
    private String accountName;
    @NotBlank(message = "请填写账号")
    @Pattern(regexp = "^[A-Za-z0-9 -]{4,64}$", message = "账号只能包含字母、数字、空格与连字符，4–64 位")
    private String accountNo;
    @Pattern(regexp = "^$|^[A-Za-z0-9]{8}([A-Za-z0-9]{3})?$", message = "SWIFT Code 需要是 8 位或 11 位字母数字")
    private String swiftCode;
    @Size(max = 64, message = "国家 / 地区不能超过 64 个字符")
    private String country;
    @Size(max = 300, message = "银行地址不能超过 300 个字符")
    private String bankAddress;
    @Size(max = 32, message = "Bank Code 不能超过 32 个字符")
    private String bankCode;
    @Size(max = 32, message = "Branch Code 不能超过 32 个字符")
    private String branchCode;
    @Size(max = 200, message = "备注不能超过 200 字")
    private String remark;
}
