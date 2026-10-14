package com.zhul.erp.modules.finance.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 付款单（含供应商退款），作废后保留 */
@Data
@TableName("fin_payment")
public class PaymentDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private String paymentNo;
    /** 1-付款、2-供应商退款 */
    private Integer kind;
    private Integer payeeType;
    private Long supplierId;
    private String shopPlatform;
    private String shopName;
    private String payeeName;
    /** 1-公司对公、2-员工垫付 */
    private Integer method;
    private Long bankAccountId;
    private String advanceUser;
    private LocalDate payDate;
    private String currencyCode;
    private BigDecimal amount;
    private BigDecimal amountCny;
    private BigDecimal actualRate;
    private BigDecimal creditUsed;
    private String remark;
    /** 0-不适用、1-待报销、2-已进报销单 */
    private Integer reimburseStatus;
    private String idempotencyKey;
    private LocalDateTime voidAt;
    private String voidBy;
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
