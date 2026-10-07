package com.zhul.erp.modules.quotation.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhul.erp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 定价策略：低值耗材金额分层；tenant_id=0 为平台默认 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pricing_amount_tier")
public class PricingAmountTierDO extends BaseEntity {
    private Integer tenantId;
    /** 采购成本价上限（CNY，含） */
    private BigDecimal maxCost;
    private BigDecimal marginRate;
    private Integer sortOrder;
    private Integer status;
}
