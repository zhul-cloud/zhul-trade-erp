package com.zhul.erp.modules.aftersales.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 退货单 */
@Data
@TableName("sales_return")
public class SalesReturnDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private String returnNo;
    private Long afterSalesId;
    private Long soId;
    private Integer handling;
    private Integer refundFirst;
    private String carrier;
    private String trackingNo;
    private String remark;
    private Integer status;
    private LocalDateTime confirmedAt;
    private Long confirmedBy;
    @TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private LocalDate receivedDate;
    @TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private Long receivedBy;
    private String receiveNote;
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
