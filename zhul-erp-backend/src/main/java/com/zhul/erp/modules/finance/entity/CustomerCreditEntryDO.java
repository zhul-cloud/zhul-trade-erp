package com.zhul.erp.modules.finance.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 客户余额流水（spec finance/customer-credit）：余额只通过流水变化，转入为正、转出为负，人民币价值生成时锁定 */
@Data
@TableName("fin_customer_credit_entry")
public class CustomerCreditEntryDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private Long customerId;
    private String currencyCode;
    /** 所属事业部；NULL：公司没有设置事业部或公共 */
    @TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private Integer businessUnitId;
    /** 见 FinanceConstants.CREDIT_* */
    private Integer entryType;
    private BigDecimal amount;
    private BigDecimal amountCny;
    /** 先进先出排序时间：转入为发生时间，释放沿用被释放转入的时间 */
    private LocalDateTime lotTime;
    private String sourceType;
    private Long sourceId;
    private String sourceNo;
    private Long relatedEntryId;
    private String remark;
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
