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
    /** 所属事业部（department.id）；NULL：公共，或公司没有设置事业部 */
    private Integer businessUnitId;
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
    /** 已停用：原手工客户等级，由经营等级 bizGrade 代替 */
    private Integer customerGrade;
    private Integer sourceChannel;
    /** 阿里等级（L0、L1、L1+、L2、L3、L4，空表示未填） */
    private String aliLevel;
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
    /** 联系方式比较键（见 ContactKeys），登记商机时查重用 */
    private String emailKey;
    private String whatsappKey;
    private String phoneKey;
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
    // ---------------------------------------------------------------- 客户画像（openspec customer-profile）
    private String linkedin;
    private String socialUrl;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer foundedYear;
    private Integer companySize;
    /** 潜力评级 1–5，0 为未评 */
    private Integer potentialRating;
    private String mainProducts;
    private String cnSuppliers;
    /** 年度采购量估算（美元），与 estPurchaseCurrency 成对，只是估算 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal estAnnualPurchase;
    private String estPurchaseCurrency;
    private Integer qualityReq;
    private Integer priceSensitivity;
    private Integer customsAbility;
    /** JSON：[{brandId, name}] */
    private String currentBrands;
    private Integer designAbility;
    /** JSON：字典码值数组 */
    private String protocols;
    private String voltages;
    private String certifications;
    private Integer repairAbility;
    /** 经营等级计算值（0-未成交、1-S、2-A、3-B、4-C） */
    private Integer bizGradeCalc;
    /** 最终经营等级（计算值加人工调整） */
    private Integer bizGrade;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal bizGradeScore;
    /** JSON：RFM 明细 */
    private String rfmJson;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime rfmCalcAt;
    /** 人工调整：-1 下调一级、0 不调整、1 上调一级 */
    private Integer bizGradeAdjust;
    private String bizGradeAdjustReason;
    private Long bizGradeAdjustBy;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime bizGradeAdjustAt;
    /** 已完成的档案模块（位：1-基础画像、2-商业能力、4-技术偏好、8-决策人、16-宏观背景） */
    private Integer profileMask;
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
