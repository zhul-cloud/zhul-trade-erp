package com.zhul.erp.modules.purchase.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 采购订单其他费用 */
@Data
@TableName("purchase_order_fee")
public class PurchaseOrderFeeDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private Long poId;
    private String feeName;
    /** 1-费用、2-优惠（金额为正数，合计里减去） */
    private Integer feeType;
    private BigDecimal amount;
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
