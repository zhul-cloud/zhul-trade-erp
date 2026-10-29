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

/** 费用单 */
@Data
@TableName("fin_expense")
public class ExpenseDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    /** 所属事业部（department.id）；NULL：公共，或公司没有设置事业部 */
    private Integer businessUnitId;
    private String expenseNo;
    private String name;
    /** 费用类别（字典 expense_category 编码） */
    private String category;
    /** 分摊月数（1–36） */
    private Integer amortizeMonths;
    private LocalDate occurDate;
    /** 1-供应商、2-线上店铺、3-货代、4-其他 */
    private Integer payeeType;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long supplierId;
    private String shopPlatform;
    private String shopName;
    private String payeeName;
    private String currencyCode;
    private BigDecimal amount;
    private BigDecimal exchangeRate;
    private BigDecimal amountCny;
    /** 1-公司对公、2-员工垫付 */
    private Integer method;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long advanceUserId;
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
