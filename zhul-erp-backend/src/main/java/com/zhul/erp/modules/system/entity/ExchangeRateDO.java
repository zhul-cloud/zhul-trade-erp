package com.zhul.erp.modules.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhul.erp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 系统汇率：1 外币 = rate 人民币，每租户每币种一条 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("exchange_rate")
public class ExchangeRateDO extends BaseEntity {
    private Integer tenantId;
    private String currencyCode;
    private BigDecimal rate;
    /** 来源（1-手动录入） */
    private Integer source;
    private Long updatedById;
    private LocalDateTime rateTime;
    private Integer status;
}
