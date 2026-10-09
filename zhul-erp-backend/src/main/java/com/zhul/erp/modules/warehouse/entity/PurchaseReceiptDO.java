package com.zhul.erp.modules.warehouse.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 采购入库单 */
@Data
@TableName("purchase_receipt")
public class PurchaseReceiptDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private String grNo;
    private Long shipmentId;
    private Long poId;
    private LocalDate receivedDate;
    private Long receivedBy;
    /** 1-有效、2-已冲销 */
    private Integer status;
    private String reverseReason;
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
