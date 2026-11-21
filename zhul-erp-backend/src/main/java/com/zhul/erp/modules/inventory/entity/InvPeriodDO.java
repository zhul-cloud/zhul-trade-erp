package com.zhul.erp.modules.inventory.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 库存月结 */
@Data
@TableName("inv_period")
public class InvPeriodDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    /** YYYY-MM */
    private String periodMonth;
    /** 0-未结账、1-已结账 */
    private Integer status;
    private Long closedBy;
    private String closedByName;
    private LocalDateTime closedAt;
    private Long reopenedBy;
    private String reopenedByName;
    private LocalDateTime reopenedAt;
    private String reopenReason;
    private String currencyCode;
    private BigDecimal closingAmount;
    private BigDecimal cogsAmount;
    private BigDecimal adjustAmount;
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
