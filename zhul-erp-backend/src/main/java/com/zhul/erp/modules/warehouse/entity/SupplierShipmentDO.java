package com.zhul.erp.modules.warehouse.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 供应商发货单 */
@Data
@TableName("supplier_shipment")
public class SupplierShipmentDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private String sdNo;
    private Long poId;
    /** 直发货代：供应商直接发到这家货代，为空表示发到福州仓库 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long directForwarderId;
    /** 直发货放进的出运单 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long logisticsId;
    /** 1-采购员登记、2-仓库补登 */
    private Integer source;
    private String carrier;
    private String trackingNo;
    private LocalDate shipDate;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate expectedArrivalDate;
    /** 1-在途、2-已入库、3-已作废 */
    private Integer status;
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
