package com.zhul.erp.modules.finance.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 历史迁移订单的成本核算快照（取自旧系统《成本核算》，金额为人民币）：迁移订单的收入、成本、毛利取此表 */
@Data
@TableName("sales_order_cost_snapshot")
public class SalesOrderCostSnapshotDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private Long soId;
    private String currencyCode;
    /** 销售收入（毛额） */
    private BigDecimal revenue;
    private BigDecimal refund;
    private BigDecimal purchaseAmount;
    private BigDecimal freight;
    private BigDecimal surcharge;
    private BigDecimal serviceFee;
    /** 实际成本合计（旧系统「销售成本」） */
    private BigDecimal cost;
    private BigDecimal grossProfit;
    private BigDecimal marginRate;
    /** 0-未结算、1-已结算 */
    private Integer settled;
    /** 核算月份 YYYY-MM */
    private String accountingMonth;
    /** 来源核算单旧编号 */
    private String sourceCodes;
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
