package com.zhul.erp.modules.masterdata.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 客户单证主体（随客户一起提交）。id 为空表示新增；请求里没有的已有记录会被删除 */
@Data
public class CustomerPartyRequest {
    private Long id;
    @NotNull(message = "请选择单证类型")
    @Min(value = 1, message = "单证类型不正确")
    @Max(value = 3, message = "单证类型不正确")
    private Integer partyType;
    @NotBlank(message = "请填写单证公司名称（英文）")
    @Size(max = 200, message = "公司名称不能超过200个字符")
    private String companyName;
    @NotBlank(message = "请选择单证国家/地区")
    private String country;
    @Size(max = 100, message = "州/省不能超过100个字符")
    private String state;
    @Size(max = 100, message = "城市不能超过100个字符")
    private String city;
    @Size(max = 20, message = "邮编不能超过20个字符")
    private String postcode;
    @NotBlank(message = "请填写单证详细地址（英文）")
    @Size(max = 300, message = "详细地址不能超过300个字符")
    private String address;
    @Size(max = 100, message = "联系人不能超过100个字符")
    private String contactName;
    @Pattern(regexp = "^[+0-9 ()\\-]{0,30}$", message = "电话只能包含 + 数字 空格 - 括号，最多30位")
    private String phone;
    @Email(message = "请输入正确的邮箱格式")
    @Size(max = 100, message = "邮箱不能超过100个字符")
    private String email;
    @Size(max = 50, message = "税号不能超过50个字符")
    private String taxId;
    @Size(max = 100, message = "目的港不能超过100个字符")
    private String destinationPort;
    /** 是否设为该类型的默认记录 */
    private Boolean defaultParty;
    @Size(max = 200, message = "备注不能超过200个字符")
    private String remark;
}
