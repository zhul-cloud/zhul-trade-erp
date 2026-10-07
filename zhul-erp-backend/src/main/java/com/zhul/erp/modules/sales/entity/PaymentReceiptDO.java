package com.zhul.erp.modules.sales.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 收款登记：业务员上传的水单、财务登记的到账（记在 PI 上） */
@Data
@TableName("payment_receipt")
public class PaymentReceiptDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private Long piId;
    private Integer kind;
    private String currencyCode;
    private BigDecimal amount;
    private BigDecimal exchangeRate;
    private BigDecimal amountCny;
    private BigDecimal feeDiff;
    private LocalDate receiptDate;
    private Integer bankAccountId;
    private Long slipId;
    private String fileKeys;
    private String note;
    private Integer status;
    private String voidReason;
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
