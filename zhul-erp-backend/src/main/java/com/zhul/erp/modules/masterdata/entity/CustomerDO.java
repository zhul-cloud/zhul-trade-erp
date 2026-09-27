package com.zhul.erp.modules.masterdata.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 客户（外贸档案）。码值见 V1.2.9__customer_trade_profile.sql 与 CustomerConstants。
 * depositRatio、paymentDays、creditLimit 的更新策略是 ALWAYS（null 也写回，用于清空），
 * 所以 updateById 只能传完整查出来的实体，不能 new 一个只带部分字段的对象去更新。
 */
@Data
@TableName("customer")
public class CustomerDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private String customerCode;
    /** 客户名称（英文法定全称） */
    private String name;
    /** 规范化名称，与 country 一起查重 */
    private String nameKey;
    private String nameCn;
    private String shortName;
    private Integer customerRole;
    private Integer industry;
    private String website;
    private Integer customerGrade;
    private Integer sourceChannel;
    /** 负责业务员 user_basic.id，0 为未分配 */
    private Long ownerId;
    /** 小满客户编号 */
    private String externalRef;
    private String country;
    private String state;
    private String city;
    private String postcode;
    private String address;
    private String taxId;
    private String timezone;
    private String contactName;
    private String contactTitle;
    private String contactPhone;
    private String contactEmail;
    private String whatsapp;
    private String otherIm;
    private String currency;
    private String incoterm;
    private String incotermPlace;
    private Integer paymentMethod;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer depositRatio;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer paymentDays;
    /** 信用额度（原币），与 creditCurrency 成对 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal creditLimit;
    private String creditCurrency;
    private Integer shippingMethod;
    private String destinationPort;
    private String remark;
    /** 状态（0-禁用、1-启用） */
    private Integer status;
    private LocalDateTime deletedAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT)
    private String createBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;
}
