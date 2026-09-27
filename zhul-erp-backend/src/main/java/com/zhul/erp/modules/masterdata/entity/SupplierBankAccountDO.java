package com.zhul.erp.modules.masterdata.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 供应商收款账户（对公 / 对私）。账号明文存储、出参脱敏；身份证号为 AES 密文。 */
@Data
@TableName("supplier_bank_account")
public class SupplierBankAccountDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private Long supplierId;
    /** 账户类型（1-对公、2-对私） */
    private Integer accountType;
    /** 户名；对私为收款人姓名 */
    private String accountName;
    private String bankName;
    private String accountNo;
    /** 收款人手机号（对私） */
    private String payeePhone;
    /** 收款人身份证号 AES 密文（对私） */
    private String payeeIdNo;
    /** 是否默认（0-否、1-是） */
    private Integer isDefault;
    private Integer sortOrder;
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
