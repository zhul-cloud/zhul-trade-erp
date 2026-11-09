package com.zhul.erp.modules.crm.followup.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 一次跟进涉及的单据：对客户记一次跟进覆盖多张单据时每张一行，只增不改 */
@Data
@TableName("customer_followup_item")
public class CustomerFollowupItemDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private Long followupId;
    private Long followupItemId;
    private String docNo;
    private String stageCode;
    private LocalDate dueDate;
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
