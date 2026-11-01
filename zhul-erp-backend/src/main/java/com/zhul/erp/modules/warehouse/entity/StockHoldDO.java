package com.zhul.erp.modules.warehouse.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 闲置库存 */
@Data
@TableName("stock_hold")
public class StockHoldDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    /** 来源（1-到货差异、2-订单部分取消） */
    private Integer sourceType;
    private Long discrepancyId;
    private Long receiptId;
    private Long poId;
    /** 来源销售订单（订单部分取消），否则 0 */
    private Long soId;
    private Long soItemId;
    /** 来源退货单（售后退货），否则 0 */
    private Long returnId;
    private String model;
    private String brand;
    private String category;
    private Integer quantity;
    private BigDecimal costPrice;
    private String locationNote;
    /** 1-暂存中、2-已退回、3-已报废、4-已转样品 */
    private Integer status;
    private String returnCarrier;
    private String returnTrackingNo;
    private BigDecimal returnFreight;
    private Long handledBy;
    private LocalDateTime handledAt;
    private String handleNote;
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
