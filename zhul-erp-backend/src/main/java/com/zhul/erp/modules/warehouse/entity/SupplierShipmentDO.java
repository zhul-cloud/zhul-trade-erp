package com.zhul.erp.modules.warehouse.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
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
    /** 1-采购员登记、2-仓库补登 */
    private Integer source;
    private String carrier;
    private String trackingNo;
    private LocalDate shipDate;
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
