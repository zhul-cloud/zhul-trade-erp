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

/** 退货去向 */
@Data
@TableName("sales_return_disposition")
public class SalesReturnDispositionDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private Long returnId;
    private Long returnItemId;
    private Long soId;
    private Long soItemId;
    private Integer disposition;
    private Integer quantity;
    private Long supplierId;
    private String trackingNo;
    private BigDecimal freightCny;
    private BigDecimal supplierRefundCny;
    private BigDecimal costCny;
    private Long holdId;
    private String remark;
    private Long operatorId;
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
