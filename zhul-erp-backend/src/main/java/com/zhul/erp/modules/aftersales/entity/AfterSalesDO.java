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
