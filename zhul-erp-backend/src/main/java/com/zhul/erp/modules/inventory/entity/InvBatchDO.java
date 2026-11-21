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

/** 库存批次 */
@Data
@TableName("inv_batch")
public class InvBatchDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private String batchNo;
    private Long productId;
    private String grade;
    /** 1-采购入库、2-退货入库、3-系统外入库、4-期初 */
    private Integer sourceType;
    private Long sourceId;
    private Long sourceItemId;
    private Long poItemId;
    private Long soItemId;
    private LocalDate inDate;
    private String currencyCode;
    private BigDecimal unitPrice;
    private BigDecimal exchangeRate;
    /** 单位成本（人民币）= 四项之和 */
    private BigDecimal unitPriceCny;
    private BigDecimal costGoods;
    private BigDecimal costInvoice;
    private BigDecimal costFee;
    private BigDecimal costClaim;
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
