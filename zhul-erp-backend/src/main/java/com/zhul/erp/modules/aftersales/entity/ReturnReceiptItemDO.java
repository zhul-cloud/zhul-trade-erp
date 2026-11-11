package com.zhul.erp.modules.aftersales.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 退货入库单行 */
@Data
@TableName("return_receipt_item")
public class ReturnReceiptItemDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private Long receiptId;
    private Long returnItemId;
    private Long soItemId;
    private String model;
    private String brand;
    private Integer quantity;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer receivedQty;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer qualifiedQty;
    private Integer disposedQty;
    private String note;
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
