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

/** 付款核销明细：实付、欠款抵扣或退款冲减 */
@Data
@TableName("fin_payment_allocation")
public class PaymentAllocationDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private Long paymentId;
    private Long payableId;
    private BigDecimal amount;
    private BigDecimal amountCny;
    /** 0-实付、1-欠款抵扣（creditPayableId 为欠款来源）、2-退款冲减欠款 */
    private Integer fromCredit;
    private Long creditPayableId;
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
