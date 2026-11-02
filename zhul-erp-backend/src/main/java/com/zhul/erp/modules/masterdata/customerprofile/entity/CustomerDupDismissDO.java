package com.zhul.erp.modules.masterdata.customerprofile.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 疑似重复客户里标为「不是重复」的组 */
@Data
@TableName("customer_dup_dismiss")
public class CustomerDupDismissDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    /** 成组原因 + 排序后的客户ID */
    private String groupKey;
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
