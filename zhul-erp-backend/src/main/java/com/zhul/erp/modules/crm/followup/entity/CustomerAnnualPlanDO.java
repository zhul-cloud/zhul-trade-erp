package com.zhul.erp.modules.crm.followup.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 战略客户的年度计划（第三期 design D7）：一个客户一年一行，目标金额为本位币 CNY */
@Data
@TableName("customer_annual_plan")
public class CustomerAnnualPlanDO {

    @TableId(type = IdType.AUTO)
    private Integer id;
    private Integer tenantId;
    private Long customerId;
    private Integer planYear;
    private BigDecimal targetAmount;
    private String currencyCode;
    private String focus;
    private String note;
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
