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

/** 结汇单：外币到账攒批结汇，按到账日期先进先出分摊 */
@Data
@TableName("fin_fx_settlement")
public class FxSettlementDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private String settleNo;
    private String currencyCode;
    private LocalDate settleDate;
    /** 结汇金额（外币） */
    private BigDecimal amount;
    /** 实际入账人民币 */
    private BigDecimal amountCny;
    /** 结汇汇率（6 位小数） */
    private BigDecimal exchangeRate;
    private Integer bankAccountId;
    private String remark;
    /** 迁移生成的不参与先进先出与估算汇率 */
    private Integer migrated;
    /** 1-有效、2-已作废 */
    private Integer status;
    private String voidReason;
    private String voidedBy;
    private LocalDateTime voidedAt;
    private String idempotencyKey;
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
