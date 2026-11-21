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

/** 成本调整单明细：每个批次按在库、已出运、其他出库拆成多行 */
@Data
@TableName("inv_cost_adjust_item")
public class InvCostAdjustItemDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private Long adjustId;
    private Long batchId;
    private Long productId;
    /** 1-在库、2-已出运、3-其他出库 */
    private Integer part;
    private Long warehouseId;
    private String docType;
    private Long docId;
    private String docNo;
    private Long soItemId;
    private Integer quantity;
    private BigDecimal oldUnitCost;
    private BigDecimal newUnitCost;
    private BigDecimal diffGoods;
    private BigDecimal diffInvoice;
    private BigDecimal diffFee;
    private BigDecimal diffClaim;
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
