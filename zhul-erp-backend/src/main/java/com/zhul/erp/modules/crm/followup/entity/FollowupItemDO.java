package com.zhul.erp.modules.crm.followup.entity;

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

/** 跟进对象：由单据事件维护、每晚核对兜底，码值见 V1.5.5__customer_followup.sql 字段注释 */
@Data
@TableName("followup_item")
public class FollowupItemDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private Integer board;
    private Integer targetType;
    private Long targetId;
    private String docNo;
    private String sourceKey;
    private Long customerId;
    private Long opportunityId;
    private Long ownerId;
    private String stageCode;
    private Integer stageGroup;
    private String currencyCode;
    private BigDecimal amount;
    private BigDecimal receivedAmount;
    private BigDecimal amountCny;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime sentAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate cadenceFrom;
    private String blockerCode;
    private String nextAction;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate nextActionDate;
    private Integer nextActionSource;
    private Integer snoozeCount;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate expectedPayDate;
    private Long lastFollowupId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime lastFollowupAt;
    private Integer noReplyStreak;
    private Integer feedbackSeen;
    private String nurtureReason;
    private String nurtureNote;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate nurtureUntil;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime closedAt;
    private Integer closeReason;
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
