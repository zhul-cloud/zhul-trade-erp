package com.zhul.erp.modules.inquiry.inquiryorder.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 型号×报价来源交叉报价表。关联 inquiry_order_supplier_id（不直接存 supplier_id），
 * 正式供应商与电商询价渠道两种来源统一取报价方信息，见 design.md 决策11。
 */
@Data
@TableName("inquiry_order_item_quote")
public class InquiryOrderItemQuoteDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private Long inquiryOrderItemId;
    private Long inquiryOrderSupplierId;
    private BigDecimal quotePriceOriginal;
    private String currencyCode;
    private BigDecimal exchangeRate;
    /** 本位币金额；汇率未维护时为 null，前端应展示为"未计算"而非0元，见任务5.6 */
    private BigDecimal quotePriceCny;
    private String supplierDelivery;
    /** 报价状态，见 QuoteStatus */
    private Integer quoteStatus;
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
