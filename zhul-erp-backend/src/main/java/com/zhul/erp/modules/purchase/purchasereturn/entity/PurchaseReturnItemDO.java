package com.zhul.erp.modules.purchase.purchasereturn.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 采购退货单行 */
@Data
@TableName("purchase_return_item")
public class PurchaseReturnItemDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private Long purchaseReturnId;
    private Long receiptItemId;
    private Long dispositionId;
    private Long discrepancyId;
    private Long soItemId;
    private Long poItemId;
    /** 库存退回：出库的仓库、批次与库存状态（1-合格、2-不良） */
    private Long warehouseId;
    private Long batchId;
    private Integer stockStatus;
    /** 引用的商品ID（由上一张单据带下来，spec product/document-product-link）；NULL 表示还没关联 */
    private Long productId;
    private String model;
    private String brand;
    private Integer quantity;
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
