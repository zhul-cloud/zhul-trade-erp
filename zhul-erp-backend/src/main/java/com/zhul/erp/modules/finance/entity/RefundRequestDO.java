package com.zhul.erp.modules.finance.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 退款申请 */
@Data
@TableName("fin_refund_request")
public class RefundRequestDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private String requestNo;
    private Long afterSalesId;
    private Long soId;
    private Long salesReturnId;
    private Integer refundFirst;
    private String currencyCode;
    private BigDecimal amount;
    private String reason;
    private Integer method;
    private String payeeInfo;
    private String remark;
    private Integer status;
    private Long applicantId;
    private Long approverId;
    private LocalDateTime approvedAt;
    private Long handlerId;
    private LocalDateTime handledAt;
    private String rejectReason;
    @TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private Long refundId;
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
