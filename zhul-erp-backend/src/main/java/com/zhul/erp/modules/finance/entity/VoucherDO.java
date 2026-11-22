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

/** 业务凭证（spec finance/business-voucher）：一张来源单据的一次库存动作一张 */
@Data
@TableName("fin_voucher")
public class VoucherDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private Long periodId;
    private String periodMonth;
    /** 1-采购入库、2-销售成本结转、3-其他出库、4-成本调整 */
    private Integer category;
    private String sourceDocType;
    private Long sourceDocId;
    private String sourceDocNo;
    private String bizType;
    private LocalDate voucherDate;
    private String summary;
    private String currencyCode;
    private BigDecimal debitTotal;
    private BigDecimal creditTotal;
    /** 1-有效、2-已作废 */
    private Integer status;
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
