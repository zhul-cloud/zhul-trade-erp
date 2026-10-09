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

/** 采购单行：一条采购需求在这张采购单上的数量与价格 */
@Data
@TableName("purchase_order_item")
public class PurchaseOrderItemDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private Long poId;
    private Long requirementId;
    private Long soId;
    private Long soItemId;
    private String model;
    private String brand;
    private Integer quantity;
    /** 单价（原币，含税与否按单头），草稿可为空 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal unitPrice;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal netPriceCny;
    private BigDecimal targetPrice;
    private BigDecimal amount;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal bargainAmount;
    private Integer sortOrder;
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
