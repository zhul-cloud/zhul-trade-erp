package com.zhul.erp.modules.masterdata.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 客户详情 / 编辑取数：全部档案字段 + 负责人部门 + 单证主体 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class CustomerDetailVO extends CustomerVO {
    private String ownerDeptName;
    private Integer industry;
    private String website;
    private String externalRef;
    private String state;
    private String city;
    private String postcode;
    private String address;
    private String taxId;
    private String timezone;
    private String contactTitle;
    private String whatsapp;
    private String otherIm;
    private String currency;
    private String incoterm;
    private String incotermPlace;
    private Integer paymentMethod;
    private Integer depositRatio;
    private Integer paymentDays;
    /** 信用额度（原币），币种见 creditCurrency */
    private BigDecimal creditLimit;
    private String creditCurrency;
    private Integer shippingMethod;
    private String destinationPort;
    private String remark;
    private List<CustomerPartyVO> parties;
    /** 中文名称来源：5-AI 翻译时编辑页显示「AI 翻译」与时间，其余为人工或空 */
    private Integer nameCnSource;
    private LocalDateTime nameCnSourceTime;
}
