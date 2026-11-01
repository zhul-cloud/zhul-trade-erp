package com.zhul.erp.modules.finance.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 客户余额先进先出消耗明细：转出流水消耗了哪几笔转入、各取多少锁定价值；释放时原样退回 */
@Data
@TableName("fin_customer_credit_consumption")
public class CustomerCreditConsumptionDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private Long outEntryId;
    private Long inEntryId;
    private BigDecimal amount;
    private BigDecimal amountCny;
    /** 0-否、1-是 */
    private Integer released;
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
