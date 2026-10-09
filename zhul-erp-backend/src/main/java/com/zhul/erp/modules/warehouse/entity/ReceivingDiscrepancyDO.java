package com.zhul.erp.modules.warehouse.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 到货差异 */
@Data
@TableName("receiving_discrepancy")
public class ReceivingDiscrepancyDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private Long receiptId;
    private Long receiptItemId;
    private Long poId;
    private Long poItemId;
    private Long purchaserId;
    private String model;
    private String brand;
    private String category;
    /** 1-少发、2-不良、3-多发 */
    private Integer type;
    private Integer quantity;
    /** 1-待处理、2-已处理 */
    private Integer status;
    /** 0-未处理、1-等补发、2-不补了、3-退货换货、4-退货不补、5-折价接收、6-退回供应商、7-暂存 */
    private Integer resolution;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal discountAmount;
    private String returnCarrier;
    private String returnTrackingNo;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal returnFreight;
    private Integer freeOfCharge;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long handledBy;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime handledAt;
    private String note;
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
