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

/** 客户退款单：冲减订单收入，按退款月份计入经营统计 */
@Data
@TableName("fin_customer_refund")
public class CustomerRefundDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private String refundNo;
    private Long soId;
    private LocalDate refundDate;
    /** 字典 refund_reason 编码 */
    private String reason;
    private String currencyCode;
    private BigDecimal amount;
    private BigDecimal exchangeRate;
    private BigDecimal amountCny;
    /** 1-原路退回平台、2-银行汇款 */
    private Integer method;
    private String remark;
    /** 1-有效、2-已作废 */
    private Integer status;
    private String voidReason;
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
