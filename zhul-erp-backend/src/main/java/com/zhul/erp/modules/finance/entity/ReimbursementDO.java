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

/** 报销单 */
@Data
@TableName("fin_reimbursement")
public class ReimbursementDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private String reimburseNo;
    private Long applicantId;
    private String applicantName;
    private LocalDateTime submittedAt;
    private Integer lineCount;
    private BigDecimal totalAmount;
    /** 1-待付款、2-已报销、3-已撤回 */
    private Integer status;
    private String withdrawReason;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long payAccountId;
    /** 付款方式：1-公司对公、3-私对私 */
    private Integer payMethod;
    /** 私对私时的付款个人账户 */
    private String payPayerAccount;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate payDate;
    private String paidBy;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime paidAt;
    private String payRemark;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long paymentId;
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
