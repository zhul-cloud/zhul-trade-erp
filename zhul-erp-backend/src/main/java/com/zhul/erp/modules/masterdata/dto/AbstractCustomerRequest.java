package com.zhul.erp.modules.masterdata.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 新增、更新客户共用的字段与格式校验。选填文本允许空串（表示未填 / 清空）。
 * 跨字段规则（交易条件联动、英文字段、国家清单、时区）在服务层校验。
 * parties 为 null 表示不修改单证主体（询盘快速创建不传），空列表表示全部删除。
 */
@Data
public abstract class AbstractCustomerRequest {
    @NotBlank(message = "客户名称不能为空")
    @Size(max = 200, message = "客户名称不能超过200个字符")
    private String name;
    @Size(max = 100, message = "中文名称不能超过100个字符")
    private String nameCn;
    @Size(max = 50, message = "客户简称不能超过50个字符")
    private String shortName;
    @Min(value = 1, message = "客户角色不正确")
    @Max(value = 7, message = "客户角色不正确")
    private Integer customerRole;
    @Min(value = 0, message = "应用行业不正确")
    @Max(value = 13, message = "应用行业不正确")
    private Integer industry;
    @Pattern(regexp = "^$|^https?://\\S{1,190}$", message = "官网需以 http:// 或 https:// 开头")
    private String website;
    @Min(value = 0, message = "客户等级不正确")
    @Max(value = 3, message = "客户等级不正确")
    private Integer customerGrade;
    @Min(value = 0, message = "客户来源不正确")
    @Max(value = 8, message = "客户来源不正确")
    private Integer sourceChannel;
    @Size(max = 50, message = "小满客户编号不能超过50个字符")
    private String externalRef;
    @Size(max = 500, message = "备注不能超过500个字符")
    private String remark;

    @NotBlank(message = "请选择国家/地区")
    private String country;
    @Size(max = 100, message = "州/省不能超过100个字符")
    private String state;
    @Size(max = 100, message = "城市不能超过100个字符")
    private String city;
    @Size(max = 20, message = "邮编不能超过20个字符")
    private String postcode;
    @Size(max = 300, message = "详细地址不能超过300个字符")
    private String address;
    @Size(max = 50, message = "税号不能超过50个字符")
    private String taxId;
    @Size(max = 64, message = "时区不正确")
    private String timezone;

    @Size(max = 100, message = "联系人姓名不能超过100个字符")
    private String contactName;
    @Size(max = 50, message = "职位不能超过50个字符")
    private String contactTitle;
    @Email(message = "请输入正确的邮箱格式")
    @Size(max = 100, message = "邮箱不能超过100个字符")
    private String contactEmail;
    @Pattern(regexp = "^[+0-9 ()\\-]{0,30}$", message = "电话只能包含 + 数字 空格 - 括号，最多30位")
    private String contactPhone;
    @Pattern(regexp = "^[+0-9 ()\\-]{0,30}$", message = "WhatsApp 只能包含 + 数字 空格 - 括号，最多30位")
    private String whatsapp;
    @Size(max = 100, message = "其他联系方式不能超过100个字符")
    private String otherIm;

    @Pattern(regexp = "^$|^(USD|EUR|GBP|JPY|CNY)$", message = "默认币种不正确")
    private String currency;
    @Pattern(regexp = "^$|^(EXW|FCA|FOB|CFR|CIF|CPT|CIP|DAP|DPU|DDP)$", message = "贸易术语不正确")
    private String incoterm;
    @Size(max = 100, message = "术语地点不能超过100个字符")
    private String incotermPlace;
    @Min(value = 0, message = "付款方式不正确")
    @Max(value = 9, message = "付款方式不正确")
    private Integer paymentMethod;
    @Min(value = 0, message = "定金比例需在0-100之间")
    @Max(value = 100, message = "定金比例需在0-100之间")
    private Integer depositRatio;
    @Min(value = 0, message = "账期需在0-365天之间")
    @Max(value = 365, message = "账期需在0-365天之间")
    private Integer paymentDays;
    @DecimalMin(value = "0", message = "信用额度不能为负数")
    @Digits(integer = 16, fraction = 2, message = "信用额度最多保留两位小数")
    private BigDecimal creditLimit;
    @Pattern(regexp = "^$|^(USD|EUR|GBP|JPY|CNY)$", message = "信用额度币种不正确")
    private String creditCurrency;
    @Min(value = 0, message = "运输方式不正确")
    @Max(value = 6, message = "运输方式不正确")
    private Integer shippingMethod;
    @Size(max = 100, message = "目的港不能超过100个字符")
    private String destinationPort;

    @Min(value = 0, message = "状态不正确")
    @Max(value = 1, message = "状态不正确")
    private Integer status;

    @Valid
    @Size(max = 20, message = "每个客户最多 20 条单证信息")
    private List<CustomerPartyRequest> parties;
}
