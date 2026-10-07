package com.zhul.erp.modules.sales.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 销售订单：由 PI 转成，创建后不可修改，只能取消 */
@Data
@TableName("sales_order")
public class SalesOrderDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private String soNo;
    private Long piId;
    private Integer piVersionNo;
    private Long customerId;
    private Long ownerId;
    private String currencyCode;
    private BigDecimal exchangeRate;
    private String buyerJson;
    private String consigneeJson;
    private String deliveryTime;
    private String paymentTerm;
    private String incoterm;
    private String incotermPlace;
    private String portOfShipment;
    private String remark;
    private BigDecimal discountAmount;
    private BigDecimal discountAmountCny;
    private BigDecimal itemAmount;
    private BigDecimal feeAmount;
    private BigDecimal totalAmount;
    private BigDecimal totalAmountCny;
    private BigDecimal netProfit;
    private BigDecimal netProfitCny;
    private BigDecimal marginRate;
    private Integer status;
    private String cancelReason;
    private Long cancelledBy;
    private LocalDateTime cancelledAt;
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
