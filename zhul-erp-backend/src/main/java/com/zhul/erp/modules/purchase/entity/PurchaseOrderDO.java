package com.zhul.erp.modules.purchase.entity;

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

/** 采购单 */
@Data
@TableName("purchase_order")
public class PurchaseOrderDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    /** 确认下单时生成，草稿为空 */
    private String poNo;
    private Long supplierId;
    private Long purchaserId;
    /** 1-草稿、2-已下单、3-已取消 */
    private Integer status;
    private LocalDate orderDate;
    private LocalDateTime orderedAt;
    private String currencyCode;
    private BigDecimal exchangeRate;
    /** 0-不含税、1-含税 */
    private Integer taxIncluded;
    private BigDecimal taxRate;
    private BigDecimal itemAmount;
    private BigDecimal feeAmount;
    private BigDecimal totalAmount;
    private BigDecimal totalAmountCny;
    private BigDecimal targetAmount;
    private BigDecimal bargainAmount;
    /** JSON，见 PaymentTerms；空串为未填 */
    private String paymentTerms;
    private String contractNo;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal contractAmount;
    private String cancelReason;
    private Long cancelledBy;
    private LocalDateTime cancelledAt;
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
