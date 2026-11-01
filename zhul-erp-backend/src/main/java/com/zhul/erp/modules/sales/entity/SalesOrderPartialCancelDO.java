package com.zhul.erp.modules.sales.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 销售订单部分取消记录：每次按型号取消一条 */
@Data
@TableName("sales_order_partial_cancel")
public class SalesOrderPartialCancelDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private Long soId;
    private Long soItemId;
    private String model;
    private Integer quantity;
    private String currencyCode;
    private BigDecimal unitPrice;
    private BigDecimal amount;
    private BigDecimal amountCny;
    private BigDecimal totalBefore;
    private BigDecimal totalAfter;
    /** 字典 partial_cancel_reason 编码 */
    private String reason;
    private String remark;
    /** 采购与库存处理结果摘要 */
    private String result;
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
