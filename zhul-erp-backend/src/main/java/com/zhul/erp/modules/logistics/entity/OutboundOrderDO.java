package com.zhul.erp.modules.logistics.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 销售出库单 */
@Data
@TableName("outbound_order")
public class OutboundOrderDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private String obNo;
    private Long soId;
    private Long customerId;
    private Long ownerId;
    private Long forwarderId;
    /** 1-发货通知、2-直发货代 */
    private Integer source;
    /** 1-待打包、2-已打包、3-已交货代、4-已撤回 */
    private Integer status;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long courierWaybillId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long logisticsId;
    private Long supplierShipmentId;
    private String note;
    private String withdrawReason;
    private Long packedBy;
    private LocalDateTime packedAt;
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
