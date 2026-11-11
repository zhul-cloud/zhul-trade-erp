package com.zhul.erp.modules.finance.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 退款申请明细 */
@Data
@TableName("fin_refund_request_item")
public class RefundRequestItemDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private Long requestId;
    private Integer lineType;
    private Long soItemId;
    private Long salesReturnItemId;
    private String itemName;
    private String brand;
    private Integer quantity;
    private BigDecimal unitPrice;
    private String currencyCode;
    private BigDecimal amount;
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
