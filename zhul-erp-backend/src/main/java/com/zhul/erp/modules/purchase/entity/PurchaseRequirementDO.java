package com.zhul.erp.modules.purchase.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 采购需求：销售订单型号行（或其拆分）待向供应商采购的数量 */
@Data
@TableName("purchase_requirement")
public class PurchaseRequirementDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private Long soId;
    private Long soItemId;
    private Long quotationItemId;
    /** 引用的商品ID（由上一张单据带下来，spec product/document-product-link）；NULL 表示还没关联 */
    private Long productId;
    private String model;
    private String brand;
    private String category;
    /** 描述（中文，来自销售订单型号行） */
    private String description;
    private Integer quantity;
    /** 删除的数量（不需采购，openspec purchase-requirement-delete），0 表示没有删除 */
    private Integer removedQty;
    /** 删除原因，如「仓库有库存：仓库有 100 个现货」 */
    private String removeReason;
    /** 目标价（CNY，不含税），为空表示没有 */
    private BigDecimal targetPrice;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long purchaserId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long suggestedSupplierId;
    /** 0-无、1-淘宝、2-1688、3-闲鱼、4-供应商、5-其他 */
    private Integer suggestedChannel;
    private String suggestedShopName;
    /** 1-有效、2-已关闭、3-订单已取消 */
    private Integer status;
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
