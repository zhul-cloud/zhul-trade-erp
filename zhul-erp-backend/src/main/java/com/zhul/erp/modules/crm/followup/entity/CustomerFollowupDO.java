package com.zhul.erp.modules.crm.followup.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 客户跟进记录：业务员记的跟进与系统事件，只增不改（design D8） */
@Data
@TableName("customer_followup")
public class CustomerFollowupDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private Integer recordType;
    private Long customerId;
    private Long followupItemId;
    private Integer targetType;
    private Long targetId;
    private Long opportunityId;
    private String title;
    private Integer situation;
    private String blockerCode;
    private String nextAction;
    private LocalDate nextActionDate;
    private Integer channel;
    private String rawContent;
    private String aiSummary;
    private Long aiTaskId;
    private String stageBefore;
    private String stageAfter;
    private LocalDate expectedPayDate;
    /** 写入档案的字段，JSON 数组：field、label、value */
    private String profileFields;
    private Long operatorId;
    private String idempotencyKey;
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
