package com.zhul.erp.modules.finance.entity;

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

/** 应付单付款计划的一期 */
@Data
@TableName("fin_payable_installment")
public class PayableInstallmentDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private Long payableId;
    private Integer seq;
    private BigDecimal ratio;
    /** 1-下单后、2-发货前、3-入库后（offsetDays 天）、5-对账确认 */
    private Integer triggerType;
    private Integer offsetDays;
    private BigDecimal amount;
    /** 为空表示待入库 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate dueDate;
    private BigDecimal paidAmount;
    private Integer status;
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
