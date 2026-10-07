package com.zhul.erp.modules.sales.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** PI 表头：编号、客户、币种与汇率、状态、版本指针、收款汇总 */
@Data
@TableName("proforma_invoice")
public class ProformaInvoiceDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private String piNo;
    private Long customerId;
    private Long ownerId;
    private String currencyCode;
    private BigDecimal exchangeRate;
    private LocalDateTime rateTime;
    private Integer status;
    private Integer currentVersionNo;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer editingVersionNo;
    private Integer itemCount;
    private BigDecimal totalAmount;
    private BigDecimal totalAmountCny;
    private Integer receiptStatus;
    private BigDecimal receivedAmount;
    private BigDecimal feeDiffAmount;
    private LocalDateTime sentAt;
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
