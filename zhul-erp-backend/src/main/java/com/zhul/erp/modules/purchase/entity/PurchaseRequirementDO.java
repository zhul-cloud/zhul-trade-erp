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
    private String model;
    private String brand;
    private Integer quantity;
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
