package com.zhul.erp.modules.quotation.entity;

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

/** 报价单 */
@Data
@TableName("quotation")
public class QuotationDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private String quotationNo;
    private Long customerId;
    private Long ownerId;
    private String currencyCode;
    private BigDecimal exchangeRate;
    private LocalDateTime rateTime;
    private String incoterm;
    private String incotermPlace;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate validUntil;
    private String remark;
    /** 状态，见 QuotationConstants.STATUS_* */
    private Integer status;
    private String lostReason;
    private String lostReasonName;
    private String lostNote;
    private Long copiedFromId;
    private BigDecimal itemAmount;
    private BigDecimal feeAmount;
    private BigDecimal totalAmount;
    private BigDecimal totalAmountCny;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal netProfit;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal netProfitCny;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal marginRate;
    private LocalDateTime sentAt;
    private LocalDateTime closedAt;
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
