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

/** 月结快照：库存台账（月份 × 仓库 × 商品） */
@Data
@TableName("inv_period_ledger")
public class InvPeriodLedgerDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private Long periodId;
    private String periodMonth;
    private Long warehouseId;
    private Long productId;
    /** 结账时商品所属的一级品类 ID，0 为未分类 */
    private Long categoryL1Id;
    private Integer openingQty;
    private BigDecimal openingAmount;
    private Integer inQty;
    private BigDecimal inAmount;
    private Integer outQty;
    private BigDecimal outAmount;
    private BigDecimal adjustAmount;
    private Integer closingQty;
    private BigDecimal closingAmount;
    private String currencyCode;
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
