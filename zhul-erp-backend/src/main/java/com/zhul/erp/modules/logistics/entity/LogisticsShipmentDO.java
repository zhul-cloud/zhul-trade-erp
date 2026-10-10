package com.zhul.erp.modules.logistics.entity;

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

/** 出运单 */
@Data
@TableName("logistics_shipment")
public class LogisticsShipmentDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private String shNo;
    private Long customerId;
    private Long forwarderId;
    private Long ownerId;
    /** 1-待出运、2-已出运、3-已作废 */
    private Integer status;
    private String carrier;
    private String waybillNo;
    private LocalDate shippedDate;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal freight;
    private Integer reconciled;
    private String voidReason;
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
