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

/** 采购订单 */
@Data
@TableName("purchase_order")
public class PurchaseOrderDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    /** 确认下单时生成，草稿为空 */
    private String poNo;
    /** 线上店铺时为 0 */
    private Long supplierId;
    /** 4-供应商；1-淘宝、2-1688、3-闲鱼、5-其他为线上店铺 */
    private Integer channel;
    private String shopName;
    private Long purchaserId;
    /** 1-草稿、2-已下单、3-已取消 */
    private Integer status;
    private LocalDate orderDate;
    private LocalDateTime orderedAt;
    /** 预计发货日期：确认下单时必填 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate expectedShipDate;
    private String currencyCode;
    private BigDecimal exchangeRate;
    /** 旧字段：含税与税率，已由开票取代，不再读写 */
    private Integer taxIncluded;
    private BigDecimal taxRate;
    /** 开票（0-不开票、1-开票） */
    private Integer invoiceRequired;
    /** 开票税点（百分点） */
    private BigDecimal taxPoint;
    /** 开票金额（原币）；为空时按货款合计 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal invoiceAmount;
    /** 开票成本 = 开票金额 × 税点（原币，计入合计） */
    private BigDecimal invoiceCost;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate expectedInvoiceDate;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate invoicedDate;
    /** 0-不开票、1-待开票、2-已开票 */
    private Integer invoiceStatus;
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
    /** 历史迁移（0-否、1-是）；迁移数据只读，见 MigratedGuard */
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
