package com.zhul.erp.modules.masterdata.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 客户单证主体：收货人 / 通知方 / 发票抬头 */
@Data
@TableName("customer_party")
public class CustomerPartyDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private Long customerId;
    /** 类型（1-收货人、2-通知方、3-发票抬头） */
    private Integer partyType;
    private String companyName;
    private String country;
    private String state;
    private String city;
    private String postcode;
    private String address;
    private String contactName;
    private String phone;
    private String email;
    private String taxId;
    private String destinationPort;
    /** 是否该类型的默认记录（0-否、1-是） */
    private Integer isDefault;
    private String remark;
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
