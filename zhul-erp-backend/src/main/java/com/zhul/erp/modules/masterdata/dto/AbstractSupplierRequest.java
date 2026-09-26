package com.zhul.erp.modules.masterdata.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 新增、更新供应商共用的字段与校验。选填的文本字段允许空串（表示未填 / 清空）。
 * country 只有询盘内联创建和旧接口在用，管理页不传；更新时为 null 表示不修改。
 * productScopes 为 null 表示不修改主营产品，空列表表示清空。
 */
@Data
public abstract class AbstractSupplierRequest {
    @NotBlank(message = "供应商名称不能为空")
    @Size(max = 100, message = "供应商名称不能超过100个字符")
    private String name;
    @Size(max = 50, message = "供应商简称不能超过50个字符")
    private String shortName;
    @Min(value = 1, message = "供应商类型不正确")
    @Max(value = 5, message = "供应商类型不正确")
    private Integer supplierType;
    @Min(value = 1, message = "所属行业不正确")
    @Max(value = 6, message = "所属行业不正确")
    private Integer industry;
    @Pattern(regexp = "^([0-9A-Za-z]{18})?$", message = "请输入正确格式的统一社会信用代码（18位）")
    private String creditCode;
    @Size(max = 50, message = "法人代表不能超过50个字符")
    private String legalRepresentative;
    @DecimalMin(value = "0", message = "注册资本不能为负数")
    @Digits(integer = 16, fraction = 2, message = "注册资本最多保留两位小数")
    private BigDecimal registeredCapital;
    @PastOrPresent(message = "成立日期不能晚于今天")
    private LocalDate establishedDate;
    private String country;
    @Size(max = 50, message = "联系人不能超过50个字符")
    private String contactName;
    @Size(max = 20, message = "联系电话不能超过20个字符")
    private String contactPhone;
    @Email(message = "请输入正确的邮箱格式")
    @Size(max = 100, message = "联系邮箱不能超过100个字符")
    private String contactEmail;
    @Size(max = 100, message = "所在地区不能超过100个字符")
    private String region;
    @Size(max = 200, message = "详细地址不能超过200个字符")
    private String address;
    @Size(max = 100, message = "开户银行不能超过100个字符")
    private String bankName;
    @Pattern(regexp = "^\\d{0,30}$", message = "银行账号只能包含数字，最多30位")
    private String bankAccount;
    @Valid
    @Size(max = 50, message = "主营品牌最多 50 个")
    private List<SupplierProductScopeRequest> productScopes;
    @Size(max = 500, message = "备注不能超过500个字符")
    private String remark;
}
