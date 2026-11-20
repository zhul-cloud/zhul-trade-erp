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

/** 库存余额（仓库 × 商品 × 批次 × 状态） */
@Data
@TableName("inv_balance")
public class InvBalanceDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private Long warehouseId;
    private Long productId;
    private Long batchId;
    private Integer stockStatus;
    private Integer onHand;
    private Integer reserved;
    private LocalDateTime lastMoveTime;
    private Integer version;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT)
    private String createBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;
}
