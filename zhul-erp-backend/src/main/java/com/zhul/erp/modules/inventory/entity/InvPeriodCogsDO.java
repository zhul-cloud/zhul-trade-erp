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

/** 月结快照：销售成本 */
@Data
@TableName("inv_period_cogs")
public class InvPeriodCogsDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private Long periodId;
    private String periodMonth;
    /** 1-出运结转、2-成本调整 */
    private Integer kind;
    private Long soItemId;
    private Long productId;
    private Long docId;
    private String docNo;
    private LocalDate bizDate;
    private Integer quantity;
    private Integer systemQty;
    private String currencyCode;
    private BigDecimal amountGoods;
    private BigDecimal amountInvoice;
    private BigDecimal amountFee;
    private BigDecimal amountClaim;
    private BigDecimal amountCny;
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
