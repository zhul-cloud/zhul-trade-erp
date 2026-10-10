package com.zhul.erp.modules.quotation.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zhul.erp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 定价策略：品相毛利率与红线（%）；tenant_id=0 为平台默认 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pricing_condition_margin")
public class PricingConditionMarginDO extends BaseEntity {
    private Integer tenantId;
    private Integer itemCondition;
    /** 为空表示不给建议值（品相待查） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal marginRate;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal floorRate;
    private Integer status;
}
