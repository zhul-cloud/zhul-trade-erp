package com.zhul.erp.modules.masterdata.customerprofile.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 客户联系人与决策人画像；主联系人的联系方式同步到 customer.contact_* */
@Data
@TableName("customer_contact")
public class CustomerContactDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private Long customerId;
    /** 是否主联系人（0-否、1-是） */
    private Integer isPrimary;
    private String name;
    private String title;
    private String email;
    private String phone;
    private String whatsapp;
    private String otherIm;
    private String emailKey;
    private String whatsappKey;
    private String phoneKey;
    private Integer jobRole;
    private Integer decisionRole;
    private Integer commStyle;
    private Integer disc;
    private Integer chinaAttitude;
    private String activeHours;
    private String likes;
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
