package com.zhul.erp.modules.purchase.purchasereturn.entity;

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

/** 采购退货单（PR） */
@Data
@TableName("purchase_return")
public class PurchaseReturnDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private String returnNo;
    private Long supplierId;
    private Integer sourceType;
    private Long poId;
    private Long returnReceiptId;
    private Long purchaseReceiptId;
    private Long afterSalesId;
    private Long soId;
    private Long ownerId;
    private Integer status;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate shipDate;
    private String carrier;
    private String trackingNo;
    private BigDecimal freightCny;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long payerId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate signedDate;
    private String currencyCode;
    private BigDecimal exchangeRate;
    private BigDecimal refundAmount;
    private BigDecimal refundCny;
    private String settleNote;
    private String remark;
    private String voidReason;
    private Integer migrated;
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
