package com.zhul.erp.modules.masterdata.customerprofile.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 客户画像字段来源：每个客户每个字段一条 */
@Data
@TableName("customer_field_source")
public class CustomerFieldSourceDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private Long customerId;
    /** 画像字段键；联系人字段为 contact.{联系人ID}.{字段} */
    private String fieldKey;
    /** 来源（1-人工、2-背调报告、3-迁移） */
    private Integer source;
    private Long reportId;
    private Long operatorId;
    /** 已问客户、等回复：提问人，0 为没有在等 */
    private Long askedBy;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime askedAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT)
    private String createBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;
}
