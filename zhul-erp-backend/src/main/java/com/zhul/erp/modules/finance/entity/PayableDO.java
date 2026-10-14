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

/** 应付单：一张来源单据（采购订单、运费账单）一张，不删除，取消时为已关闭 */
@Data
@TableName("fin_payable")
public class PayableDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private String payableNo;
    /** 1-供应商、2-线上店铺、3-货代 */
    private Integer payeeType;
    private Long supplierId;
    private String shopPlatform;
    private String shopName;
    private String payeeName;
    /** 1-采购订单、2-运费账单 */
    private Integer sourceType;
    private Long sourceId;
    private String sourceNo;
    private String currencyCode;
    private BigDecimal amount;
    private BigDecimal exchangeRate;
    private BigDecimal amountCny;
    /** 净已付（原币）：核销进来的减去抵扣出去与退款 */
    private BigDecimal paidAmount;
    private BigDecimal paidCny;
    /** 供应商欠款（原币） */
    private BigDecimal creditAmount;
    /** 1-未付、2-部分付款、3-已付清、4-已关闭 */
    private Integer status;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate nextDueDate;
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
