package com.zhul.erp.modules.aftersales.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 售后单 */
@Data
@TableName("after_sales")
public class AfterSalesDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private String asNo;
    private Long customerId;
    private Long soId;
    private String levelCode;
    private String symptom;
    private String customerRequest;
    private LocalDateTime receivedAt;
    private String reasonCode;
    private String solutions;
    private Long ownerId;
    private String nextAction;
    @TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private LocalDateTime nextUpdateAt;
    /** 首次告知客户时间（首次响应） */
    private LocalDateTime respondedAt;
    /** 最近一次告知客户时间 */
    private LocalDateTime lastContactAt;
    /** 更新客户间隔的起算时间 */
    private LocalDateTime slaStartAt;
    /** 当前截止时间，为空不提醒 */
    @TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private LocalDateTime dueAt;
    /** 截止事项（0-无、1-首次响应、2-更新客户、3-手填时间） */
    private Integer dueType;
    private Integer status;
    private String result;
    private LocalDateTime closedAt;
    private String cancelReason;
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
