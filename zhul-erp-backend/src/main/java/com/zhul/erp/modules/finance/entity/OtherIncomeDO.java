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

/** 其他收入：平台返点、利息、补贴等（人民币），按月计入经营统计 */
@Data
@TableName("fin_other_income")
public class OtherIncomeDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private String incomeNo;
    private LocalDate incomeDate;
    /** 字典 other_income_category 编码 */
    private String category;
    private BigDecimal amount;
    private String remark;
    /** 1-有效、2-已作废 */
    private Integer status;
    private String voidReason;
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
