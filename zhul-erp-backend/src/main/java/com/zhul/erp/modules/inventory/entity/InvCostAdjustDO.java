package com.zhul.erp.modules.inventory.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 成本调整单（系统自动生成，只能查看） */
@Data
@TableName("inv_cost_adjust")
public class InvCostAdjustDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private String adjustNo;
    private LocalDate adjustDate;
    /** 所属月份 YYYY-MM */
    private String periodMonth;
    /** 触发原因（InvConstants.CA_*） */
    private Integer reason;
    private String sourceDocType;
    private Long sourceDocId;
    private String sourceDocNo;
    private Integer batchCount;
    private String firstModel;
    private String currencyCode;
    /** 在库调整合计 */
    private BigDecimal stockAmount;
    /** 销售成本调整合计 */
    private BigDecimal cogsAmount;
    /** 其他出库调整合计 */
    private BigDecimal otherAmount;
    private String remark;
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
