package com.zhul.erp.modules.aftersales.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 退货入库单（RR） */
@Data
@TableName("return_receipt")
public class ReturnReceiptDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private String receiptNo;
    private Long salesReturnId;
    private Long afterSalesId;
    private Long soId;
    private Long ownerId;
    private Integer status;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate receivedDate;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long receivedBy;
    private String receiveNote;
    private String remark;
    private String voidReason;
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
