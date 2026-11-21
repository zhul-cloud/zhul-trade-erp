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

/** 库存流水（只增不改） */
@Data
@TableName("inv_move")
public class InvMoveDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private LocalDateTime moveTime;
    private Long warehouseId;
    private Long productId;
    private Long batchId;
    /** 1-合格、2-不良 */
    private Integer stockStatus;
    /** 1-入库、2-出库、3-调入、4-调出、5-转入、6-转出、7-预占、8-释放 */
    private Integer opType;
    private Integer quantity;
    private Integer targetType;
    private Long targetId;
    private BigDecimal unitPriceCny;
    private BigDecimal amountCny;
    private String bizType;
    private String bizLabel;
    private String docType;
    private Long docId;
    private String docNo;
    private Long docItemId;
    private Long reverseOf;
    private Long operatorId;
    private String operatorName;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT)
    private String createBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;
}
