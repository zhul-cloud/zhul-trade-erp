package com.zhul.erp.modules.system.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 系统汇率变更记录 */
@Data
@TableName("exchange_rate_log")
public class ExchangeRateLogDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private String currencyCode;
    private BigDecimal oldRate;
    private BigDecimal newRate;
    private Long operatorId;
    private LocalDateTime operatedAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT)
    private String createBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;
}
