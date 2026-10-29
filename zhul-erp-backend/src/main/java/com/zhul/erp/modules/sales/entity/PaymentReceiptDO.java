package com.zhul.erp.modules.sales.entity;

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

/** 收款登记：业务员上传的水单、财务登记的到账、平台收款；PI 为空时是还没认领的到账 */
@Data
@TableName("payment_receipt")
public class PaymentReceiptDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    /** 所属事业部（department.id）；NULL：公共，或公司没有设置事业部 */
    private Integer businessUnitId;
    /** 收款账户与订单的事业部不一致（0-否、1-是） */
    private Integer buMismatch;
    /** 为空表示未认领到账 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long piId;
    /** 手动创建的订单的收款；PI 与订单都为空表示未认领到账 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long soId;
    private Integer kind;
    private String currencyCode;
    private String paymentMethod;
    private String paymentMethodName;
    /** 1-线下、2-线上 */
    private Integer channel;
    private String platformOrderNo;
    private BigDecimal amount;
    private BigDecimal exchangeRate;
    private BigDecimal amountCny;
    private BigDecimal feeDiff;
    private BigDecimal platformFee;
    /** 实收（原币）= 到账金额 − 平台手续费 */
    private BigDecimal netAmount;
    /** 实收人民币 */
    private BigDecimal netAmountCny;
    /** 1-系统汇率、2-实际入账反算 */
    private Integer rateSource;
    /** 已结汇金额（原币，实收口径） */
    private BigDecimal settledAmount;
    /** 结汇状态：0-不适用（人民币或水单）、1-未结汇、2-部分结汇、3-已结汇 */
    private Integer settleStatus;
    private String payer;
    private LocalDate receiptDate;
    private Integer bankAccountId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long slipId;
    private String fileKeys;
    private String note;
    private Integer status;
    private String voidReason;
    private Long operatorId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long claimedBy;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime claimedAt;
    /** 历史迁移（0-否、1-是）；迁移数据只读，见 MigratedGuard */
    private Integer migrated;
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
