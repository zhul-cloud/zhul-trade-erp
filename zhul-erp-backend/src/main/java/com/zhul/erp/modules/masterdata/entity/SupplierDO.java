package com.zhul.erp.modules.masterdata.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 供应商。注意 registeredCapital、establishedDate 的更新策略是 ALWAYS（null 也写回，用于清空），
 * 所以 updateById 只能传完整查出来的实体，不能 new 一个只带部分字段的对象去更新。
 */
@Data
@TableName("supplier")
public class SupplierDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    /** 供应商编码（租户内未删除记录唯一，创建后不可修改） */
    private String supplierCode;
    private String name;
    private String shortName;
    /** 供应商类型（0-未设置、1-生产商、2-经销商、3-服务商、4-代理商、5-其他） */
    private Integer supplierType;
    /** 所属行业（0-未设置、1-制造业、2-原材料、3-信息技术、4-物流运输、5-金融服务、6-其他） */
    private Integer industry;
    /** 统一社会信用代码（18位大写字母数字） */
    private String creditCode;
    private String legalRepresentative;
    /** 注册资本（万元人民币）；可清空，所以更新时 null 也要写回 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal registeredCapital;
    /** 成立日期；可清空，所以更新时 null 也要写回 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate establishedDate;
    private String country;
    private String contactName;
    private String contactPhone;
    private String contactEmail;
    /** 所在地区（省/市/区名称，用/分隔） */
    private String region;
    private String address;
    private String bankName;
    /** 银行账号明文，对外展示一律经 SensitiveDataMasker 脱敏 */
    private String bankAccount;
    /** 主营品牌（逗号分隔，仅辅助展示，不做强校验） */
    private String mainBrands;
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
